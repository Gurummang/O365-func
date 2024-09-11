package com.GASB.o365_func.service;

import com.GASB.o365_func.model.dto.MsFileCountDto;
import com.GASB.o365_func.model.dto.MsFileInfoDto;
import com.GASB.o365_func.model.dto.MsFileSizeDto;
import com.GASB.o365_func.model.dto.MsRecentFileDTO;
import com.GASB.o365_func.model.entity.FileUploadTable;
import com.GASB.o365_func.model.entity.OrgSaaS;
import com.GASB.o365_func.model.mapper.MsFileMapper;
import com.GASB.o365_func.repository.ActivitiesRepo;
import com.GASB.o365_func.repository.FileUploadTableRepo;
import com.GASB.o365_func.repository.OrgSaaSRepo;
import com.GASB.o365_func.repository.StoredFileRepo;
import com.GASB.o365_func.service.api_call.MsApiService;
import com.GASB.o365_func.service.util.FileDownloadUtil;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.requests.DriveItemCollectionPage;
import com.microsoft.graph.requests.GraphServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class MsFileService {

    private final MsApiService msApiService;
    private final FileDownloadUtil fileDownloadUtil;
    private final OrgSaaSRepo orgSaaSRepo;
    private final MsFileMapper msFileMapper;
    private final FileUploadTableRepo fileUploadTableRepo;
    private final StoredFileRepo storedFilesRepository;
    private final ActivitiesRepo activitiesRepo;

    @Async("threadPoolTaskExecutor")
    public CompletableFuture<Void> initFiles(int workspaceId) {
        return CompletableFuture.runAsync(() -> {
            log.info("workspaceId : {}", workspaceId);
            try {
                GraphServiceClient graphClient = msApiService.createGraphClient(workspaceId);
                List<DriveItemCollectionPage> itemPages = msApiService.fetchFileLists(graphClient);
                List<DriveItemCollectionPage> siteItems = msApiService.fetchFileListsInSite(graphClient, msApiService.fetchSiteLists(graphClient));
                OrgSaaS orgSaaSObject = orgSaaSRepo.findById(workspaceId).orElse(null);

                for (DriveItemCollectionPage itemPage : itemPages){
                    for (DriveItem item : itemPage.getCurrentPage()){
//                        loggingResult(item);
                        if(item.folder != null){ //폴더일 경우 넘긴다.
                            continue;
                        }
                        fileDownloadUtil.processAndStoreFile(msFileMapper.toOneDriveEntity(item),orgSaaSObject,workspaceId, "file_upload",graphClient);
                    }
                }

                for (DriveItemCollectionPage siteItem : siteItems){
                    for (DriveItem item : siteItem.getCurrentPage()){
//                        loggingResult(item);
                        if(item.folder != null){ //폴더일 경우 넘긴다.
                            continue;
                        }
                        MsFileInfoDto msFileInfoDto = msFileMapper.toSharePointEntity(item);
                        log.info("site_id : {}", msFileInfoDto.getSite_id());
                        fileDownloadUtil.processAndStoreFile(msFileInfoDto,orgSaaSObject,workspaceId, "file_upload",graphClient);
                    }
                }
            } catch (Exception ex) {
                log.error("Error fetching or saving users for workspaceId: {}", workspaceId, ex);
                throw new RuntimeException("Error fetching or saving users for workspaceId: " + workspaceId, ex);
            }
        }).exceptionally(ex -> {
            log.error("Async error occurred for workspaceId: {}", workspaceId, ex);
            return null;
        });
    }




    public List<MsRecentFileDTO> msRecentFiles(int orgId, int saasId) {
        try {
            return fileUploadTableRepo.findRecentFilesByOrgIdAndSaasId(orgId, saasId);
        } catch (Exception e) {
            log.error("Error retrieving recent files for org_id: {} and saas_id: {}", orgId, saasId, e);
            return Collections.emptyList();
        }
    }

    public Long getTotalFileSize(int orgId, int saasId) {
        Long totalFileSize = storedFilesRepository.getTotalFileSize(orgId, saasId);
        return totalFileSize != null ? totalFileSize : 0L; // null 반환 방지
    }

    public Long getTotalMaliciousFileSize(int orgId, int saasId) {
        Long totalMaliciousFileSize = storedFilesRepository.getTotalMaliciousFileSize(orgId, saasId);
        return totalMaliciousFileSize != null ? totalMaliciousFileSize : 0L; // null 반환 방지
    }

    public Long getTotalDlpFileSize(int orgId, int saasId) {
        Long totalDlpFileSize = storedFilesRepository.getTotalDlpFileSize(orgId, saasId);
        return totalDlpFileSize != null ? totalDlpFileSize : 0L; // null 반환 방지
    }
    public MsFileSizeDto sumOfMsFileSize(int orgId, int saasId) {
        return MsFileSizeDto.builder()
                .totalSize((float) getTotalFileSize(orgId,saasId) / 1073741824)
                .sensitiveSize((float) getTotalDlpFileSize(orgId,saasId) / 1073741824)
                .maliciousSize((float) getTotalMaliciousFileSize(orgId,saasId) / 1073741824)
                .build();
    }

    public MsFileCountDto MsFileCountSum(int orgId, int saasId) {
        return MsFileCountDto.builder()
                .totalFiles(storedFilesRepository.countTotalFiles(orgId, saasId))
                .sensitiveFiles(storedFilesRepository.countSensitiveFiles(orgId, saasId))
                .maliciousFiles(storedFilesRepository.countMaliciousFiles(orgId, saasId))
                .connectedAccounts(storedFilesRepository.countConnectedAccounts(orgId, saasId))
                .build();
    }

    public boolean fileDelete(int idx, String fileHash) {
        try {
            // 파일 ID와 해시값을 통해 파일 조회
            FileUploadTable targetFile = fileUploadTableRepo.findByIdAndFileHash(idx, fileHash).orElse(null);
            if (targetFile == null) {
                log.error("File not found or invalid: id={}, hash={}", idx, fileHash);
                return false;
            }
            // 해당 파일이 Slack 파일인지 확인
            if (orgSaaSRepo.findSaaSIdById(targetFile.getOrgSaaS().getId()) != 1) {
                log.error("File is not a Slack file: id={}, saasId={}", idx, targetFile.getOrgSaaS().getId());
                return false;
            }
            // Slack API를 통해 파일 삭제 요청
            return msApiService.MsFileDeleteApi(targetFile.getOrgSaaS().getId(), targetFile.getSaasFileId());

        } catch (Exception e) {
            log.error("Error processing file delete: id={}, hash={}", idx, fileHash, e);
            return false;
        }
    }
}

package com.GASB.o365_func.service;

import com.GASB.o365_func.model.dto.MsFileCountDto;
import com.GASB.o365_func.model.dto.MsFileInfoDto;
import com.GASB.o365_func.model.dto.MsFileSizeDto;
import com.GASB.o365_func.model.dto.MsRecentFileDTO;
import com.GASB.o365_func.model.entity.Activities;
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
import java.util.Map;
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

    public void fileDelete(List<Map<String, String>> requests) {
        try {
            // 요청된 파일 목록을 반복하여 처리
            for (Map<String, String> request : requests) {
                int fileUploadTableIdx = Integer.parseInt(request.get("id")); // 파일 ID
                String file_name = request.get("file_name"); // 파일 이름
                String path = request.get("path"); // 파일 경로

                // 파일 체크
                if (!checkFile(fileUploadTableIdx, file_name, path)) {
                    return; // 체크 실패 시 false 반환
                }
                // 파일 업로드 테이블에서 파일 정보를 조회
                FileUploadTable targetFile = fileUploadTableRepo.findById(fileUploadTableIdx).orElse(null);
                // Null 체크 추가
                if (targetFile == null) {
                    log.error("File not found with id: {}", fileUploadTableIdx);
                    return; // 파일이 존재하지 않는 경우 false 반환
                }
                // MS API를 통해 파일 삭제 요청
                msApiService.MsFileDeleteApi(targetFile.getOrgSaaS().getId(), targetFile.getSaasFileId());
            }
        } catch (RuntimeException e) {
            // 예외 발생 시 로깅
            log.error("Error processing file delete for requests: {}", requests, e);
        }
    }


    private boolean checkFile(int idx, String file_name, String path){

        FileUploadTable targetFile = fileUploadTableRepo.findById(idx).orElse(null);
        Activities targetFileActivity = activitiesRepo.findBySaasFileId(Objects.requireNonNull(targetFile).getSaasFileId()).orElse(null);
        String tmp_file_name = Objects.requireNonNull(targetFileActivity).getFileName();
        if (!tmp_file_name.equals(file_name)) {
            log.error("File name not matched: id={}, name={}", idx, file_name);
            return false;
        }
        if (orgSaaSRepo.findSaaSIdById(targetFile.getOrgSaaS().getId()) != 3) {
            log.error("File is not a Slack file: id={}, saasId={}", idx, targetFile.getOrgSaaS().getId());
            return false;
        }
        return true;
    }
}

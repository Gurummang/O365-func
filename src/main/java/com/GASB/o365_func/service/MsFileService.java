package com.GASB.o365_func.service;

import com.GASB.o365_func.model.dto.MsFileCountDto;
import com.GASB.o365_func.model.dto.MsFileSizeDto;
import com.GASB.o365_func.model.dto.MsRecentFileDTO;
import com.GASB.o365_func.model.entity.OrgSaaS;
import com.GASB.o365_func.model.mapper.MsFileMapper;
import com.GASB.o365_func.repository.ActivitiesRepo;
import com.GASB.o365_func.repository.FileUploadTableRepo;
import com.GASB.o365_func.repository.OrgSaaSRepo;
import com.GASB.o365_func.repository.StoredFileRepo;
import com.GASB.o365_func.service.api_call.MsApiService;
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
    public CompletableFuture<Void> initFiles(String email, int workspaceId) {
        return CompletableFuture.runAsync(() -> {
            log.info("workspaceId : {}", workspaceId);
            try {
                List<DriveItemCollectionPage> itemPages = msApiService.fetchFileLists();
//                String accessToken = msApiService.getAccessToken();
                GraphServiceClient graphClient = msApiService.getGraphClient();
                OrgSaaS orgSaaSObject = orgSaaSRepo.findById(workspaceId).orElse(null);

                for (DriveItemCollectionPage itemPage : itemPages){
                    for (DriveItem item : itemPage.getCurrentPage()){
                        loggingResult(item);
                        if(item.folder != null){ //폴더일 경우 넘긴다.
                            continue;
                        }
                        fileDownloadUtil.processAndStoreFile(msFileMapper.toEntity(item),orgSaaSObject,workspaceId, "file_upload",graphClient);
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

    private void loggingResult(DriveItem item) {
        log.info("------------------------------");
        log.info("ID: {}", item.id);
        log.info("Name: {}", item.name);
        log.info("Created DateTime: {}", item.createdDateTime);
        log.info("Last Modified DateTime: {}", item.lastModifiedDateTime);
        log.info("Web URL: {}", item.webUrl);
        log.info("Download URL: {}", item.additionalDataManager().get("@microsoft.graph.downloadUrl"));
        log.info("Created By: {}", (item.createdBy != null && item.createdBy.user != null ? item.createdBy.user.displayName : "N/A"));
        log.info("Created User ID: {}", (item.createdBy != null && item.createdBy.user != null ? item.createdBy.user.id : "N/A"));
        log.info("Size: {}", item.size);
        log.info("file_path : {}", Objects.requireNonNull(item.parentReference).path);
        log.info("------------------------------");
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
}

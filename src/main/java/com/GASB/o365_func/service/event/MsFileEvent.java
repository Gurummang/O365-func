package com.GASB.o365_func.service.event;


import com.GASB.o365_func.model.entity.Activities;
import com.GASB.o365_func.model.entity.OrgSaaS;
import com.GASB.o365_func.model.mapper.MsFileMapper;
import com.GASB.o365_func.repository.*;
import com.GASB.o365_func.service.api_call.MsApiService;
import com.GASB.o365_func.service.message.MessageSender;
import com.GASB.o365_func.service.util.FileDownloadUtil;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.requests.GraphServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class MsFileEvent {

    private final FileDownloadUtil fileService;
    private final MsApiService msApiService;
    private final OrgSaaSRepo orgSaaSRepo;
    private final FileUploadTableRepo fileUploadRepository;
    private final MsFileMapper msFileMapper;
    private final ActivitiesRepo fileActivityRepo;
    private final MonitoredUsersRepo monitoredUsersRepo;
    private final MessageSender messageSender;
    private final StoredFileRepo storedFileRepo;
    private final FileDownloadUtil fileDownloadUtil;
    private final FileUploadTableRepo fileUploadTableRepo;

    public void handleFileEvent(Map<String, Object> payload, String event_type) {
        log.info("Handling file event with payload: {}", payload);
        try {
            log.info("Handling file event with payload: {}", payload);
            String userId = payload.get("userId").toString().split("/")[2];
            log.info("Handling file event with userId: {}", userId);
            // 사용자 및 SaaS ID 조회
            CompletableFuture<Integer> orgSaasIdFuture = CompletableFuture.supplyAsync(() ->
                    monitoredUsersRepo.getOrgSaaSId(userId)
            );

            CompletableFuture<OrgSaaS> orgSaaSObjectFuture = orgSaasIdFuture.thenApply(org_saas_id ->
                    orgSaaSRepo.findById(org_saas_id).orElse(null)
            );

            CompletableFuture<GraphServiceClient<?>> graphClientFuture = orgSaasIdFuture.thenApply(msApiService::createGraphClient
            );

            // 모든 비동기 작업 완료 후 처리
            CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(orgSaasIdFuture, orgSaaSObjectFuture, graphClientFuture)
                    .thenComposeAsync(v -> graphClientFuture.thenComposeAsync(graphClient ->
                            msApiService.fetchDeltaInfo(userId, graphClient)
                                    .thenAcceptAsync(driveItemsWithEventType -> {
                                        try {
                                            OrgSaaS orgSaaSObject = orgSaaSObjectFuture.join();  // CompletableFuture에서 결과 가져오기
                                            int org_saas_id = orgSaasIdFuture.join();
                                            driveItemsWithEventType.forEach((driveItem, eventType) -> {
                                                log.info("Processing item: {}, EventType: {}", driveItem, eventType);
                                                switch (eventType) {
                                                    case "file_delete" -> handleFileDeleteEvent(driveItem);
                                                    case "file_change" -> fileService.processAndStoreFile(
                                                            msFileMapper.OneDriveChangeEvent(driveItem),
                                                            orgSaaSObject,
                                                            org_saas_id,
                                                            "file_change",
                                                            graphClient
                                                    );
                                                    default -> fileService.processAndStoreFile(
                                                            msFileMapper.toOneDriveEntity(driveItem),
                                                            orgSaaSObject,
                                                            org_saas_id,
                                                            "file_upload",
                                                            graphClient
                                                    );
                                                }
                                            });
                                        } catch (Exception e) {
                                            log.error("Error processing drive items: {}", e.getMessage());
                                        }
                                    }).exceptionally(ex -> {
                                        log.error("Error fetching delta info: {}", ex.getMessage());
                                        return null;
                                    })
                    )).exceptionally(ex -> {
                        log.error("Error during combined futures: {}", ex.getMessage());
                        return null;
                    });

            // 비동기 작업 중 예외 처리
            combinedFuture.exceptionally(ex -> {
                log.error("Error occurred while processing file event: {}", ex.getMessage());
                return null;
            });

        } catch (Exception e) {
            log.error("Unexpected error processing file event", e);
        }
    }


    public void handleFileDeleteEvent(DriveItem item) {
        try {
            // 1. activities 테이블에 deleted 이벤트로 추가
            String file_id = item.id;
            // 현재시각
            long timestamp = Instant.now().getEpochSecond();

            Activities activities = copyForDelete(file_id, timestamp);
            String file_hash = fileUploadRepository.findFileHashByFileId(file_id).orElse(null);
            String s3Path = storedFileRepo.findSavePathByHash(file_hash).orElse(null);
            fileActivityRepo.save(activities);

            fileDownloadUtil.deleteFileInS3(s3Path);
            // 2. file_upload 테이블에서 deleted 컬럼을 true로 변경
            fileUploadRepository.checkDelete(file_id);
            messageSender.sendGroupingMessage(activities.getId());
        } catch (Exception e) {
            log.error("Error processing file delete event", e);
        }
    }

    private Activities copyForDelete(String file_id, long timestamp){
        // 최근 활동 정보를 찾음, 없으면 null
        Activities activities = fileActivityRepo.findRecentBySaasFileId(file_id).orElse(null);
        // file_upload테이블에서 delete가 이미 1 처리 되어있으면 null 혹은 activities테이블에서 해당 saas_file_id의 file_delete 이벤트가 있을경우 null
        if (fileUploadTableRepo.checkAlreadyDelete(file_id) == 1 || fileActivityRepo.existsAlreadyDeleteFileBySaasFileId(file_id)){
            log.warn("File already deleted: {}", file_id);
            throw new IllegalStateException("File already deleted: " + file_id);
        }

        // activities가 null일 경우 예외 처리 또는 기본값 처리
        if (activities == null) {
            log.warn("No recent activities found for file_id: {}", file_id);
            throw new IllegalStateException("No recent activity found for file: " + file_id);
        }

        // 시간대를 서울로 고정하여 처리
        ZoneId zoneId = ZoneId.of("Asia/Seoul");

        // timestamp가 0일 경우, 현재 시간을 사용할 수 있도록 처리
        LocalDateTime adjustedTimestamp;
        if (timestamp > 0) {
            adjustedTimestamp = LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), zoneId);
        } else {
            adjustedTimestamp = LocalDateTime.now(zoneId);
        }

        return Activities.builder()
                .user(activities.getUser()) // null이 아닌지 확인 후 처리
                .eventType("file_delete")
                .saasFileId(file_id)
                .fileName(activities.getFileName())
                .eventTs(adjustedTimestamp)
                .uploadChannel(activities.getUploadChannel())
                .tlsh(activities.getTlsh())
                .build();
    }

}

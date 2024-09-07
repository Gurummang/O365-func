package com.GASB.o365_func.service.event;

import com.GASB.o365_func.model.dto.MsFileInfoDto;
import com.GASB.o365_func.model.entity.Activities;
import com.GASB.o365_func.model.entity.OrgSaaS;
import com.GASB.o365_func.model.mapper.MsFileMapper;
import com.GASB.o365_func.repository.*;
import com.GASB.o365_func.service.api_call.MsApiService;
import com.GASB.o365_func.service.message.MessageSender;
import com.GASB.o365_func.service.util.FileDownloadUtil;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.requests.DriveItemDeltaCollectionPage;
import com.microsoft.graph.requests.GraphServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
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
    private final MsDeltaLinkRepo msDeltaLinkRepo;

    public void handleFileEvent(Map<String, Object> payload, String event_type) {
        log.info("Handling file event with payload: {}", payload);
        try {
            String userId = payload.get("userId").toString();

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

                                            // Map<DriveItem, String> 처리
                                            driveItemsWithEventType.forEach((driveItem, eventType) -> {
                                                log.info("Processing item: {}, EventType: {}", driveItem, eventType);
                                                if (eventType.equals("file_delete")){
                                                    handleFileDeleteEvent(driveItem);
                                                } else {
                                                    fileService.processAndStoreFile(
                                                            msFileMapper.toOneDriveEntity(driveItem),
                                                            orgSaaSObject,
                                                            org_saas_id,
                                                            eventType,
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
            fileActivityRepo.save(activities);
            // 2. file_upload 테이블에서 deleted 컬럼을 true로 변경
            fileUploadRepository.checkDelete(file_id);
            messageSender.sendGroupingMessage(activities.getId());
        } catch (Exception e) {
            log.error("Error processing file delete event", e);
        }
    }

    private Activities copyForDelete(String file_id, long timestamp){
        Activities activities = fileActivityRepo.findRecentBySaasFileId(file_id).orElse(null);

        // timestamp가 0일 경우, 현재 시간을 사용할 수 있도록 처리
        LocalDateTime adjustedTimestamp;
        if (timestamp > 0) {
            adjustedTimestamp = LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.of("Asia/Seoul"));
        } else {
            adjustedTimestamp = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        }

        return Activities.builder()
                .user(activities.getUser())
                .eventType("file_delete")
                .saasFileId(file_id)
                .fileName(activities.getFileName())
                .eventTs(adjustedTimestamp)
                .uploadChannel(activities.getUploadChannel())
                .tlsh(activities.getTlsh())
                .build();
    }
}

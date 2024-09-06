package com.GASB.o365_func.service.event;

import com.GASB.o365_func.model.entity.Activities;
import com.GASB.o365_func.model.entity.OrgSaaS;
import com.GASB.o365_func.model.mapper.MsFileMapper;
import com.GASB.o365_func.repository.*;
import com.GASB.o365_func.service.api_call.MsApiService;
import com.GASB.o365_func.service.message.MessageSender;
import com.GASB.o365_func.service.util.FileDownloadUtil;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.requests.DriveItemDeltaCollectionPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

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

    public void handleFileEvent(/*Map<String,Object> payload*/String event_type) {
//        log.info("Handling file event with payload: {}", payload);
        try {
//            String userId = payload.get("userId").toString();
//            String tenantId = payload.get("tenantId").toString();

            String tmpUserId = "5c9a4995-181f-4726-b994-7410c5ab0774";

            // 이후 델타 api를 사용해서 변경사항 조회.
            // 그래프 클라이언트도 가져와야겠네
            // 아님 어차피 유저 아이디로도 조회 가능한데?
            int org_saas_id = monitoredUsersRepo.getOrgSaaSId(tmpUserId);
            OrgSaaS orgSaaSObject = orgSaaSRepo.findById(org_saas_id).orElse(null);

            msApiService.fetchDeltaInfo(tmpUserId);

//            fileService.processAndStoreFile(fileInfo, orgSaaSObject, orgSaaSObject.getId(), event);
//
//            log.info("File event processed successfully for file ID: {}", fileInfo.getId());
        } catch (Exception e) {
            log.error("Unexpected error processing file event", e);
        }
    }

//    public void handleFileDeleteEvent(Map<String, Object> payload) {
//        try {
//            log.info("event_type : {}", payload.get("event"));
//            // 1. activities 테이블에 deleted 이벤트로 추가
//            String file_id = payload.get("fileId").toString();
//            String event_ts = payload.get("timestamp").toString();
//            String file_owner_id= null, file_name = null;
//
//            long timestamp = Long.parseLong(event_ts.split("\\.")[0]);
//            Activities activities = copyForDelete(file_id, timestamp);
//            fileActivityRepo.save(activities);
//            // 2. file_upload 테이블에서 deleted 컬럼을 true로 변경
//            fileUploadRepository.checkDelete(file_id);
//            messageSender.sendGroupingMessage(activities.getId());
//        } catch (Exception e) {
//            log.error("Error processing file delete event", e);
//        }
//    }
//
//    private Activities copyForDelete(String file_id, long timestamp){
//        Activities activities = fileActivityRepo.findRecentBySaasFileId(file_id).orElse(null);
//
//        // timestamp가 0일 경우, 현재 시간을 사용할 수 있도록 처리
//        LocalDateTime adjustedTimestamp;
//        if (timestamp > 0) {
//            adjustedTimestamp = LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.of("Asia/Seoul"));
//        } else {
//            adjustedTimestamp = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
//        }
//
//        return Activities.builder()
//                .user(activities.getUser())
//                .eventType("file_delete")
//                .saasFileId(file_id)
//                .fileName(activities.getFileName())
//                .eventTs(adjustedTimestamp)
//                .uploadChannel(activities.getUploadChannel())
//                .tlsh(activities.getTlsh())
//                .build();
//    }
}

package com.GASB.o365_func.service.util;

import com.GASB.o365_func.repository.MonitoredUsersRepo;
import com.GASB.o365_func.repository.WorkSpaceConfigRepo;
import com.GASB.o365_func.service.api_call.MsApiService;
import com.microsoft.graph.http.GraphFatalServiceException;
import com.microsoft.graph.models.Request;
import com.microsoft.graph.models.Subscription;
import com.microsoft.graph.requests.GraphServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebhookUtil {



    private final WorkSpaceConfigRepo workSpaceConfigRepo;
    private final MsApiService msApiService;
    private final MonitoredUsersRepo monitoredUsersRepo;


    // 구독을 생성하는 부분
    public CompletableFuture<Void> createSubscriptionAsync(GraphServiceClient<?> graphClient, String userId, int workspaceId) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Notification URL 체크
                String notificationUrl = workSpaceConfigRepo.findWebhookUrlById(workspaceId).orElse(null);
                if (notificationUrl == null) {
                    log.error("Failed to create subscription for user {}: Notification URL is null", userId);
                    throw new IllegalArgumentException("Notification URL cannot be null");
                }

                Subscription subscription = new Subscription();
                subscription.changeType = "updated";
                subscription.notificationUrl = notificationUrl;
                subscription.resource = "/users/" + userId + "/drive/root";
                subscription.expirationDateTime = OffsetDateTime.now().plusMinutes(1440);  // 최대 구독 만료 시간 설정
                subscription.clientState = generateClientState();

                Subscription createdSubscription = graphClient.subscriptions()
                        .buildRequest()
                        .post(subscription);

                log.info("Created subscription for user {}: {}", userId, createdSubscription.id);
                log.info("Created subscription details: {}", createdSubscription);

            } catch (GraphFatalServiceException e) {
                log.error("GraphFatalServiceException :: Failed to create subscription for user {}: {} - Response code: {}",
                        userId, e.getMessage(), e.getResponseCode(), e);
            } catch (Exception e) {
                log.error("Exception :: Failed to create subscription for user {}: {} - Stack trace: ", userId, e.getMessage(), e);
            }
        });
    }


    public void createSubscriptionsForAllUsers(int id) {
        List<String> userIds = monitoredUsersRepo.getMonitoredUserList(id);

        GraphServiceClient<?> graphClient = msApiService.createGraphClient(id);

        // 명시적으로 List<CompletableFuture<Void>>로 타입을 지정
        List<CompletableFuture<Void>> futures = userIds.stream()
                .map(userId -> createSubscriptionAsync(graphClient, userId,id))
                .toList();

        // 모든 작업이 완료될 때까지 기다림
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }


    // 구독을 삭제하는 부분

    // 리프레시 토큰을 이용해 토큰을 갱신하는 부분

    // 델타 api를 이용해 변경된 파일을 가져오는 부분


    // 구독 자동 갱신
//    @Scheduled(fixedRate = 172800000) // 1시간마다 실행
//    public void renewSubscriptions() {
//        List<String> userIds = monitoredUsersRepo.getMonitoredUserList();
//
//        userIds.forEach(userId -> {
//            try {
//                // 기존 구독 ID 가져오기 로직 필요
//                String subscriptionId = monitoredUsersRepo.getSubscriptionId(userId);
//
//                // 구독 갱신
//                Subscription subscription = new Subscription();
//                subscription.expirationDateTime = OffsetDateTime.now().plusMinutes(4230);  // 최대 구독 시간
//
//                GraphServiceClient<Request> graphClient = getClient(id);
//                graphClient.subscriptions(subscriptionId)
//                        .buildRequest()
//                        .patch(subscription);
//
//                log.info("Renewed subscription for user {}: {}", userId, subscriptionId);
//
//            } catch (Exception e) {
//                log.error("Failed to renew subscription for user {}: {}", userId, e.getMessage());
//            }
//        });
//    }

    private String generateClientState() {
        return UUID.randomUUID().toString();
    }

}

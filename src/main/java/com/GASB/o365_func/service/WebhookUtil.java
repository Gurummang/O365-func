package com.GASB.o365_func.service;

import com.GASB.o365_func.repository.MonitoredUsersRepo;
import com.GASB.o365_func.repository.WorkSpaceConfigRepo;
import com.GASB.o365_func.service.api_call.MsApiService;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class WebhookUtil {



    private final WorkSpaceConfigRepo workSpaceConfigRepo;
    private final MsApiService msApiService;
    private final MonitoredUsersRepo monitoredUsersRepo;

    @Value("${webhook.url}")
    private String webhookUrl;

    private GraphServiceClient getClient(int id){
        return msApiService.createGraphClient(id);
    }

    // 구독을 생성하는 부분
    public CompletableFuture<Void> createSubscriptionAsync(GraphServiceClient<Request> graphClient, String userId) {
        return CompletableFuture.runAsync(() -> {
            try {
                Subscription subscription = new Subscription();
                subscription.changeType = "updated,deleted";
                subscription.notificationUrl = webhookUrl;
                subscription.resource = "/users/" + userId + "/drive/root";
                subscription.expirationDateTime = OffsetDateTime.now().plusMinutes(4230);  // 최대 구독 만료 시간 설정
                subscription.clientState = generateClientState();

                Subscription createdSubscription = graphClient.subscriptions()
                        .buildRequest()
                        .post(subscription);

                log.info("Created subscription for user {}: {}", userId, createdSubscription.id);

            } catch (Exception e) {
                log.error("Failed to create subscription for user {}: {}", userId, e.getMessage());
            }
        });
    }

    public void createSubscriptionsForAllUsers(GraphServiceClient<Request> graphClient, int id) {
        List<String> userIds = monitoredUsersRepo.getMonitoredUserList(id);
        List<CompletableFuture<Void>> futures = userIds.stream()
                .map(userId -> createSubscriptionAsync(graphClient, userId))
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

    public String generateClientState() {
        return UUID.randomUUID().toString();
    }

}

package com.GASB.o365_func.service;

import com.GASB.o365_func.service.util.WebhookUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class MsInitService {


    private final MsFileService msFileService;
    private final MsUserService msUserService;
    private final WebhookUtil webhookUtil;

    public CompletableFuture<Void> fetchAndSaveFiles(int workspaceId) {
        return msFileService.initFiles(workspaceId)
                .thenRun(() -> log.info("Files saved successfully"))
                .exceptionally(e -> {
                    log.error("Error fetching files: {}", e.getMessage(), e);
                    return null;
                });
    }

    public CompletableFuture<Void> fetchAndSaveUsers(int workspaceId) {
        return msUserService.fetchAndSaveUser(workspaceId)
                .thenRun(() -> log.info("Users fetched successfully"))
                .exceptionally(e -> {
                    log.error("Error fetching users: {}", e.getMessage(), e);
                    return null;
                });
    }

    public CompletableFuture<Void> setWebhook(int workspaceId){
        return CompletableFuture.runAsync(() -> {
            try {
                webhookUtil.createSubscriptionsForAllUsers(workspaceId);
            } catch (Exception e) {
                log.error("Error setting webhook: {}", e.getMessage(), e);
            }
        });
    }
    public void fetchAndSaveAll(int message) {
        try {
            // usersFuture 완료 후 filesFuture 수행
            CompletableFuture<Void> usersFuture = fetchAndSaveUsers(message)
                    .thenCompose(v -> fetchAndSaveFiles(message)) // usersFuture가 완료되면 filesFuture 호출
                    .thenCompose(v -> setWebhook(message))  // filesFuture 완료 후 웹훅 설정
                    .thenRun(() -> log.info("Webhook set successfully"))  // 웹훅 설정 후 실행
                    .thenRun(() -> log.info("All data fetched and saved successfully"))  // 모든 작업 완료 후 실행
                    .exceptionally(e -> {
                        log.error("Error fetching files, users, or setting webhook: {}", e.getMessage(), e);
                        return null;
                    });

            // 비동기 작업을 동기적으로 완료되도록 기다림
            usersFuture.join();
        } catch (Exception e) {
            log.error("Error fetching files, users, or setting webhook: {}", e.getMessage(), e);
        }
    }


}

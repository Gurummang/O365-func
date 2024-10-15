package com.GASB.o365_func.service;

import com.GASB.o365_func.service.util.WebhookUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

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
            // 모든 작업을 비동기로 실행하고 완료되기를 기다림
            CompletableFuture<Void> usersFuture = fetchAndSaveUsers(message)
                    .thenCompose(v -> fetchAndSaveFiles(message)) // 파일 저장
                    .thenCompose(v -> setWebhook(message))  // 웹훅 설정
                    .thenRun(() -> log.info("Webhook set successfully"))  // 웹훅 완료 로그
                    .thenRun(() -> log.info("All data fetched and saved successfully"))  // 모든 작업 완료 로그
                    .exceptionally(e -> {
                        log.error("Error fetching files, users, or setting webhook: {}", e.getMessage(), e);
                        return null;
                    });

            // 모든 비동기 작업 완료를 대기
            usersFuture.join();  // main 쓰레드를 블로킹하지 않으려면 다른 대기 방법 고려
        } catch (CompletionException e) {
            log.error("CompletionException: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error in fetchAndSaveAll: {}", e.getMessage(), e);
        }
    }



}

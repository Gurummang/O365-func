package com.GASB.o365_func.service;

import com.GASB.o365_func.controller.Request.RequestTest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class MsInitService {


    private final MsFileService msFileService;
    private final MsUserService msUserService;

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

    public void fetchAndSaveAll(int message) {
        CompletableFuture<Void> usersFuture = fetchAndSaveUsers(message);
        CompletableFuture<Void> filesFuture = fetchAndSaveFiles(message);
        try {
            CompletableFuture.allOf(usersFuture, filesFuture)
                    .thenRun(() -> log.info("All data fetched and saved successfully"))
                    .exceptionally(e -> {
                        log.error("Error fetching files or users: {}", e.getMessage(), e);
                        return null;
                    })
                    .join(); // 비동기 작업을 동기적으로 완료되도록 기다림
        } catch (Exception e) {
            log.error("Error fetching files or users: {}", e.getMessage(), e);
        }
    }
}

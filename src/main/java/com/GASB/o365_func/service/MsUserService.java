package com.GASB.o365_func.service;

import com.GASB.o365_func.model.dto.TopUserDTO;
import com.GASB.o365_func.model.entity.MonitoredUsers;
import com.GASB.o365_func.model.mapper.MsUserMapper;
import com.GASB.o365_func.repository.MonitoredUsersRepo;
import com.GASB.o365_func.service.api_call.MsApiService;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.UserCollectionPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.naming.AuthenticationException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MsUserService {


    private final MsApiService msApiService;
    private final MsUserMapper msUserMapper;
    private final MonitoredUsersRepo monitoredUsersRepo;


    @Async("threadPoolTaskExecutor")
    public CompletableFuture<Void> fetchAndSaveUser(int workspaceId) {
        return CompletableFuture.runAsync(() -> {
            log.info("workspaceId : {}", workspaceId);
            try {
                // 1. GraphClient 생성 시 발생할 수 있는 예외 처리
                GraphServiceClient<?> graphClient;
                try {
                    graphClient = msApiService.createGraphClient(workspaceId);
                    if (graphClient == null) {
                        throw new IllegalArgumentException("Graph client creation failed. Possible invalid token for workspaceId: " + workspaceId);
                    }
                } catch (IllegalArgumentException ex) {
                    log.error("Error creating GraphServiceClient for workspaceId: {}", workspaceId, ex);
                    throw new RuntimeException("GraphServiceClient creation failed for workspaceId: " + workspaceId, ex);
                }

                // 2. 사용자 리스트 가져올 때 발생할 수 있는 예외 처리
                UserCollectionPage users;
                try {
                    users = msApiService.fetchUsersList(graphClient);
                    if (users == null || users.getCurrentPage().isEmpty()) {
                        log.warn("No users found for workspaceId: {}", workspaceId);
                        return;  // 더 이상 진행하지 않음
                    }
                } catch (GraphServiceException ex) {
                    log.error("Error fetching users from Graph API for workspaceId: {}", workspaceId, ex);
                    throw new RuntimeException("Failed to fetch users for workspaceId: " + workspaceId, ex);
                }

                log.info("Fetched {} users for workspaceId: {}", users.getCurrentPage().size(), workspaceId);

                // 3. 사용자 엔티티로 변환 및 중복 처리
                try {
                    List<MonitoredUsers> monitoredUsers = users.getCurrentPage().stream()
                            .map(user -> msUserMapper.toEntity(user, workspaceId))
                            .collect(Collectors.toList());

                    // 중복된 user_id를 제외하고 저장할 사용자 목록 생성
                    List<MonitoredUsers> filteredUsers = monitoredUsers.stream()
                            .filter(user -> !monitoredUsersRepo.existsByUserIdAndOrgSaaS_Id(user.getUserId(), workspaceId))
                            .collect(Collectors.toList());

                    if (!filteredUsers.isEmpty()) {
                        monitoredUsersRepo.saveAll(filteredUsers);
                        log.info("Saved {} users for workspaceId: {}", filteredUsers.size(), workspaceId);
                    } else {
                        log.info("No new users to save for workspaceId: {}", workspaceId);
                    }

                } catch (DataAccessException ex) {
                    log.error("Database error while saving users for workspaceId: {}", workspaceId, ex);
                    throw new RuntimeException("Database error saving users for workspaceId: " + workspaceId, ex);
                } catch (Exception ex) {
                    log.error("Unexpected error during processing for workspaceId: {}", workspaceId, ex);
                    throw new RuntimeException("Unexpected error processing users for workspaceId: " + workspaceId, ex);
                }

            } catch (Exception ex) {
                log.error("General error fetching or saving users for workspaceId: {}", workspaceId, ex);
                throw new RuntimeException("Error fetching or saving users for workspaceId: " + workspaceId, ex);
            }
        }).exceptionally(ex -> {
            log.error("Async error occurred for workspaceId: {}", workspaceId, ex);
            if (ex instanceof CompletionException) {
                log.error("CompletionException caused by: {}", ex.getCause().getMessage());
            }
            return null;
        });
    }


    public List<TopUserDTO> getTopUsers(int orgId, int saasId) {
        try {
            List<Object[]> results = monitoredUsersRepo.findTopUsers(orgId, saasId);

            return results.stream().map(result -> new TopUserDTO(
                    (String) result[0],
                    ((Number) result[1]).longValue(),
                    ((Number) result[2]).longValue(),
                    ((java.sql.Timestamp) result[3]).toLocalDateTime()
            )).collect(Collectors.toList());

        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving top users", e);
        }
    }

    @Async("threadPoolTaskExecutor")
    public CompletableFuture<List<TopUserDTO>> getTopUsersAsync(int orgId, int saasId) {
        return CompletableFuture.supplyAsync(() -> getTopUsers(orgId, saasId));
    }


}

package com.GASB.o365_func.service;

import com.GASB.o365_func.model.dto.TopUserDTO;
import com.GASB.o365_func.model.entity.MonitoredUsers;
import com.GASB.o365_func.model.mapper.MsUserMapper;
import com.GASB.o365_func.repository.MonitoredUsersRepo;
import com.GASB.o365_func.service.api_call.MsApiService;
import com.microsoft.graph.requests.UserCollectionPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MsUserService {


    private final MsApiService msApiService;
    private final MsUserMapper msUserMapper;
    private final MonitoredUsersRepo monitoredUsersRepo;


    @Async("threadPoolTaskExecutor")
    public CompletableFuture<Void> fetchAndSaveUser(String email, int workspaceId) {
        return CompletableFuture.runAsync(() -> {
            log.info("workspaceId : {}", workspaceId);
            try {
                UserCollectionPage users = msApiService.fetchUsersList();
                log.info("orgSaaSId: {}", workspaceId);

                List<MonitoredUsers> monitoredUsers = users.getCurrentPage().stream()
                        .map(user -> msUserMapper.toEntity(user, workspaceId))
                        .collect(Collectors.toList());

                // 중복된 user_id를 제외하고 저장할 사용자 목록 생성
                List<MonitoredUsers> filteredUsers = monitoredUsers.stream()
                        .filter(user -> !monitoredUsersRepo.existsByUserIdAndOrgSaaS_Id(user.getUserId(), workspaceId))
                        .collect(Collectors.toList());

                monitoredUsersRepo.saveAll(filteredUsers);
            } catch (Exception ex) {
                log.error("Error fetching or saving users for workspaceId: {}", workspaceId, ex);
                throw new RuntimeException("Error fetching or saving users for workspaceId: " + workspaceId, ex);
            }
        }).exceptionally(ex -> {
            log.error("Async error occurred for workspaceId: {}", workspaceId, ex);
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

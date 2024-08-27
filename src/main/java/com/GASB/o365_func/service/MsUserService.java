package com.GASB.o365_func.service;

import com.GASB.o365_func.model.entity.MonitoredUsers;
import com.GASB.o365_func.model.mapper.MsUserMapper;
import com.GASB.o365_func.repository.MonitoredUsersRepo;
import com.GASB.o365_func.service.api_call.MsApiService;
import com.microsoft.graph.requests.UserCollectionPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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


}

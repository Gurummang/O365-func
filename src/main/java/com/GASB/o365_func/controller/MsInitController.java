package com.GASB.o365_func.controller;


import com.GASB.o365_func.service.MsApiService;
import com.GASB.o365_func.service.MsSharePointApiService;
import com.microsoft.graph.models.User;
import com.microsoft.graph.requests.UserCollectionPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

//테스트 컨트롤러이며 추후 RabbitMQ를 이용해 메시지를 받아 처리하는 서비스로 변경 예정
@RestController
@Slf4j
@RequestMapping("/api/v1/connect/ms")
@RequiredArgsConstructor
public class MsInitController {


    private final MsApiService msApiService;
    private final MsSharePointApiService msSharePointApiService;


    //테스트용
    @PostMapping("/users")
    public ResponseEntity<?> fetchUsers(String userId) {
        UserCollectionPage SharePointUser = msSharePointApiService.fetchSPUserList();
        log.info("SharePointUser : {}", SharePointUser);
        return ResponseEntity.ok(SharePointUser);
    }

    @PostMapping("/files")
    public ResponseEntity<?> fetchFileLists(String userId) {
        return ResponseEntity.ok(msApiService.fetchFileLists(userId));
    }



}

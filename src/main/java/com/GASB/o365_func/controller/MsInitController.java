package com.GASB.o365_func.controller;


import com.GASB.o365_func.controller.Request.RequestTest;
import com.GASB.o365_func.service.MsFileService;
import com.GASB.o365_func.service.WebhookUtil;
import com.GASB.o365_func.service.api_call.MsApiService;
import com.GASB.o365_func.service.MsUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

//테스트 컨트롤러이며 추후 RabbitMQ를 이용해 메시지를 받아 처리하는 서비스로 변경 예정
@RestController
@Slf4j
@RequestMapping("/api/v1/connect/ms")
@RequiredArgsConstructor
public class MsInitController {


    private final MsApiService msApiService;
    private final MsUserService msUserService;
    private final MsFileService msFileService;
    private final WebhookUtil webhookUtil;


    //테스트용
    @PostMapping("/users")
    public ResponseEntity<Map<String,String>> fetchUsers(@RequestBody RequestTest requestTest) {
        Map<String,String> response = new HashMap<>();
        try {
            int workspace_id = requestTest.getWorkspace_id();
            String email = requestTest.getEmail();
            msUserService.fetchAndSaveUser(workspace_id);
            response.put("status", "success");
            response.put("message", "User saved successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching users", e);
            response.put("status", "error");
            response.put("message", "Error fetching users" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

    }

    @PostMapping("/files")
    public ResponseEntity<Map<String,String>> fetchFileLists(@RequestBody RequestTest requestTest) {
        Map<String,String> response = new HashMap<>();
        try {
            int workspace_id = requestTest.getWorkspace_id();
            String email = requestTest.getEmail();
            msFileService.initFiles(workspace_id);
            response.put("status", "success");
            response.put("message", "File lists saved successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching file lists", e);
            response.put("status", "error");
            response.put("message", "Error fetching file lists" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/test/webhook")
    public ResponseEntity<Map<String,String>> testWebhook(){
        Map<String, String> response = new HashMap<>();
        try {
            msApiService.createGraphClient(174);
            webhookUtil.createSubscriptionsForAllUsers(174);
            response.put("status", "success");
            response.put("message", "Webhook test success");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error testing webhook", e);
            response.put("status", "error");
            response.put("message", "Error testing webhook" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/all")
    public ResponseEntity<Map<String,String>> fetchData(@RequestBody RequestTest requestTest){
        Map<String, String> response = new HashMap<>();
        try {
            int workspace_id = requestTest.getWorkspace_id();
            String email = requestTest.getEmail();
            msUserService.fetchAndSaveUser(workspace_id);
            msFileService.initFiles(workspace_id);
            response.put("status", "success");
            response.put("message", "Data saved successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching data", e);
            response.put("status", "error");
            response.put("message", "Error fetching data" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}

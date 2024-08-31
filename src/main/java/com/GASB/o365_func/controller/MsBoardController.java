package com.GASB.o365_func.controller;

import com.GASB.o365_func.annotation.JWT.ValidateJWT;
import com.GASB.o365_func.model.dto.MsFileCountDto;
import com.GASB.o365_func.model.dto.MsFileSizeDto;
import com.GASB.o365_func.model.dto.MsRecentFileDTO;
import com.GASB.o365_func.model.dto.TopUserDTO;
import com.GASB.o365_func.model.entity.Saas;
import com.GASB.o365_func.repository.AdminUsersRepo;
import com.GASB.o365_func.repository.FileUploadTableRepo;
import com.GASB.o365_func.repository.SaasRepo;
import com.GASB.o365_func.repository.StoredFileRepo;
import com.GASB.o365_func.service.MsFileService;
import com.GASB.o365_func.service.MsUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/board/o365")
public class MsBoardController {


    private final MsFileService msFileService;
    private final MsUserService msUserService;
    private final SaasRepo saasRepo;
    private final AdminUsersRepo adminUsersRepo;



    @GetMapping("/files/size")
    @ValidateJWT
    public ResponseEntity<?> fetchFilesize(HttpServletRequest servletRequest) {
        try{
            if (servletRequest.getAttribute("error") != null) {
                String errorMessage = (String) servletRequest.getAttribute("error");
                Map<String, String> errorResponse = new HashMap<>();
                log.error("Error fetching user ranking in user-ranking api: {}", errorMessage);
                errorResponse.put("status", "401");
                errorResponse.put("error_message", errorMessage);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            String email = (String) servletRequest.getAttribute("email");
            int orgId = adminUsersRepo.findByEmail(email).get().getOrg().getId();
            MsFileSizeDto msFileSizeDto = msFileService.sumOfMsFileSize(orgId,3);
            return ResponseEntity.ok(msFileSizeDto);
        } catch (Exception e){
            log.error("Error fetching file size", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MsFileSizeDto(0,0,0));
        }
    }


    @GetMapping("/files/count")
    @ValidateJWT
    public ResponseEntity<?> fetchFileCount(HttpServletRequest servletRequest) {
        try {
            if (servletRequest.getAttribute("error") != null) {
                String errorMessage = (String) servletRequest.getAttribute("error");
                Map<String, String> errorResponse = new HashMap<>();
                log.error("Error fetching user ranking in user-ranking api: {}", errorMessage);
                errorResponse.put("status", "401");
                errorResponse.put("error_message", errorMessage);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            String email = (String) servletRequest.getAttribute("email");
            int orgId = adminUsersRepo.findByEmail(email).get().getOrg().getId();
            MsFileCountDto msFileCountDto = msFileService.MsFileCountSum(orgId,3);
            return ResponseEntity.ok(msFileCountDto);
        } catch (Exception e) {
            log.error("Error fetching file count", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MsFileCountDto(0,0,0,0));
        }
    }


    @GetMapping("/files/recent")
    @ValidateJWT
    public ResponseEntity<?> fetchRecentFiles(HttpServletRequest servletRequest) {
        try {
            if (servletRequest.getAttribute("error") != null) {
                String errorMessage = (String) servletRequest.getAttribute("error");
                Map<String, String> errorResponse = new HashMap<>();
                log.error("Error fetching user ranking in user-ranking api: {}", errorMessage);
                errorResponse.put("status", "401");
                errorResponse.put("error_message", errorMessage);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            String email = (String) servletRequest.getAttribute("email");
            int orgId = adminUsersRepo.findByEmail(email).get().getOrg().getId();
            Saas saasObjcet = saasRepo.findById(3).orElse(null);
//            Saas saasObject = saasRepo.findBySaasName("o365").orElse(null);
            List<MsRecentFileDTO> recentFiles = msFileService.msRecentFiles(orgId, saasObjcet.getId().intValue());
            return ResponseEntity.ok(recentFiles);
        } catch (Exception e){
            log.error("Error fetching recent files", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonList(new MsRecentFileDTO("Error", "Server Error", "N/A", LocalDateTime.now())));
        }
    }


    @GetMapping("/user-ranking")
    @ValidateJWT
    public ResponseEntity<?> fetchUserRanking(HttpServletRequest servletRequest) {
        try {
            if (servletRequest.getAttribute("error") != null) {
                String errorMessage = (String) servletRequest.getAttribute("error");
                Map<String, String> errorResponse = new HashMap<>();
                log.error("Error fetching user ranking in user-ranking api: {}", errorMessage);
                errorResponse.put("status", "401");
                errorResponse.put("error_message", errorMessage);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            String email = (String) servletRequest.getAttribute("email");
            int orgId = adminUsersRepo.findByEmail(email).get().getOrg().getId();
            Saas saasObjcet = saasRepo.findById(3).orElse(null);
//            Saas saasObject = saasRepo.findBySaasName("o365").orElse(null);
            CompletableFuture<List<TopUserDTO>> future = msUserService.getTopUsersAsync(orgId, saasObjcet.getId().intValue());
            List<TopUserDTO> topuser = future.get();
            return ResponseEntity.ok(topuser);
        } catch (Exception e) {
            log.error("Error fetching user ranking", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonList(new MsRecentFileDTO("Error", "Server Error", "N/A", LocalDateTime.now())));
        }
    }

}

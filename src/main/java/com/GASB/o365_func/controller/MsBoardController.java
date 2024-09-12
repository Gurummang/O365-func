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
import org.springframework.web.bind.annotation.*;

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
                log.error("Error fetching file size: {}", errorMessage);
                errorResponse.put("status", "401");
                errorResponse.put("error_message", errorMessage);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            String email = (String) servletRequest.getAttribute("email");
            int orgId = adminUsersRepo.findByEmail(email);;
            MsFileSizeDto msFileSizeDto = msFileService.sumOfMsFileSize(orgId,3);
            return ResponseEntity.ok(msFileSizeDto);
        } catch (Exception e){
            log.error("Error fetching file size");
            log.error("Error Message : {}", e.getMessage());
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
                log.error("Error fetching file count : {}", errorMessage);
                errorResponse.put("status", "401");
                errorResponse.put("error_message", errorMessage);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            String email = (String) servletRequest.getAttribute("email");
            int orgId = adminUsersRepo.findByEmail(email);
            MsFileCountDto msFileCountDto = msFileService.MsFileCountSum(orgId,3);
            return ResponseEntity.ok(msFileCountDto);
        } catch (Exception e) {
            log.error("Error fetching file count");
            log.error("error_message : {} ", e.getMessage());
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
                log.error("Error fetching recent file api: {}", errorMessage);
                errorResponse.put("status", "401");
                errorResponse.put("error_message", errorMessage);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            String email = (String) servletRequest.getAttribute("email");
            int orgId = adminUsersRepo.findByEmail(email);
            Saas saasObjcet = saasRepo.findById(3).orElse(null);
//            Saas saasObject = saasRepo.findBySaasName("o365").orElse(null);
            List<MsRecentFileDTO> recentFiles = msFileService.msRecentFiles(orgId, saasObjcet.getId().intValue());
            return ResponseEntity.ok(recentFiles);
        } catch (Exception e){
            log.error("Error fetching recent files");
            log.error("error_message : {} ", e.getMessage());
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
            int orgId = adminUsersRepo.findByEmail(email);
            Saas saasObjcet = saasRepo.findById(3).orElse(null);
//            Saas saasObject = saasRepo.findBySaasName("o365").orElse(null);
            CompletableFuture<List<TopUserDTO>> future = msUserService.getTopUsersAsync(orgId, saasObjcet.getId().intValue());
            List<TopUserDTO> topuser = future.get();
            return ResponseEntity.ok(topuser);
        } catch (Exception e) {
            log.error("Error fetching user ranking");
            log.error("error_message : {}",e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonList(new MsRecentFileDTO("Error", "Server Error", "N/A", LocalDateTime.now())));
        }
    }


    @PostMapping("/files/delete")
//    @ValidateJWT
    public ResponseEntity<?> deleteFile(/*HttpServletRequest servletRequest, */@RequestBody Map<String, String> request) {
        // 아마 delete에는 해시값이 필요하지 않을까..?
        try {
//            if (servletRequest.getAttribute("error") != null) {
//                String errorMessage = (String) servletRequest.getAttribute("error");
//                Map<String, String> errorResponse = new HashMap<>();
//                log.error("Error fetching user ranking in user-ranking api: {}", errorMessage);
//                errorResponse.put("status", "401");
//                errorResponse.put("error_message", errorMessage);
//                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
//            }
            int fileUploadTableIdx = Integer.parseInt(request.get("id"));
            String file_hash = request.get("file_hash");
            Map<String, String> response = new HashMap<>();
            if (msFileService.fileDelete(fileUploadTableIdx,file_hash)){
                response.put("status","200");
                response.put("message","file deleted successful");
            } else {
                response.put("status","404");
                response.put("message","file deleted failed");
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // log.error("Error fetching recent files", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonList(new TopUserDTO("Error", 0L, 0L, LocalDateTime.now())));
        }
    }

}

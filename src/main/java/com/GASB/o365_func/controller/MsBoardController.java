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
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.exception.NullArgumentException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.FileNotFoundException;
import java.io.IOException;
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
        } catch (IllegalArgumentException e) {
            log.error("Error fetching file size: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("message", "Error fetching file size"));
        } catch (NullPointerException e) {
            log.error("Error fetching file size: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("message", "Error fetching file size"));
        } catch (Exception e) {
            log.error("Error fetching file size: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("message", "Error fetching file size"));
        }
    }


    @GetMapping("/files/count")
    @ValidateJWT
    public ResponseEntity<?> fetchFileCount(HttpServletRequest servletRequest) {
        try {
            // JWT 인증 오류 처리
            if (servletRequest.getAttribute("error") != null) {
                String errorMessage = (String) servletRequest.getAttribute("error");
                Map<String, String> errorResponse = new HashMap<>();
                log.error("Unauthorized access while fetching file count: {}", errorMessage);
                errorResponse.put("status", "401");
                errorResponse.put("error_message", errorMessage);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }

            // 이메일 및 조직 ID 가져오기
            String email = (String) servletRequest.getAttribute("email");
            if (email == null) {
                log.warn("Email attribute is missing in the request.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("message", "Email is missing"));
            }

            // 조직 ID 가져오기
            int orgId = adminUsersRepo.findByEmail(email);
            MsFileCountDto msFileCountDto = msFileService.MsFileCountSum(orgId, 3);
            return ResponseEntity.ok(msFileCountDto);

        } catch (NullPointerException e) {
            // NullPointerException 처리
            log.error("NullPointerException occurred while fetching file count", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MsFileCountDto(0, 0, 0, 0));

        } catch (ClassCastException e) {
            // ClassCastException 처리
            log.error("ClassCastException occurred while fetching file count. Incorrect attribute type?", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MsFileCountDto(0, 0, 0, 0));

        } catch (Exception e) {
            // 모든 다른 예외 처리
            log.error("Unexpected error occurred while fetching file count", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MsFileCountDto(0, 0, 0, 0));
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

    @GetMapping("/files/recent")
    @ValidateJWT
    public ResponseEntity<?> fetchRecentFiles(HttpServletRequest servletRequest) {
        try {
            // JWT 인증 오류 처리
            if (servletRequest.getAttribute("error") != null) {
                String errorMessage = (String) servletRequest.getAttribute("error");
                Map<String, String> errorResponse = new HashMap<>();
                log.error("Unauthorized access while fetching recent files: {}", errorMessage);
                errorResponse.put("status", "401");
                errorResponse.put("error_message", errorMessage);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }

            // 이메일과 조직 ID 가져오기
            String email = (String) servletRequest.getAttribute("email");
            if (email == null) {
                log.warn("Email attribute is missing in the request.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("message", "Email is missing"));
            }

            int orgId = adminUsersRepo.findByEmail(email);
            Saas saasObject = saasRepo.findById(3).orElseThrow(() -> new EntityNotFoundException("Saas not found"));

            List<MsRecentFileDTO> recentFiles = msFileService.msRecentFiles(orgId, saasObject.getId().intValue());
            return ResponseEntity.ok(recentFiles);

        } catch (EntityNotFoundException e) {
            log.error("Saas entity not found", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.singletonMap("message", "Saas entity not found"));
        } catch (Exception e) {
            log.error("Unexpected error fetching recent files", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonList(new MsRecentFileDTO("Error", "Server Error", "N/A", LocalDateTime.now())));
        }
    }



    @PostMapping("/files/delete")
    @ValidateJWT
    public ResponseEntity<?> deleteFiles(HttpServletRequest servletRequest, @RequestBody List<Map<String, String>> requests) {
        try {
            // JWT 인증 오류 처리
            if (servletRequest.getAttribute("error") != null) {
                String errorMessage = (String) servletRequest.getAttribute("error");
                Map<String, String> errorResponse = new HashMap<>();
                log.error("Error fetching user ranking in user-ranking api: {}", errorMessage);
                errorResponse.put("status", "401");
                errorResponse.put("error_message", errorMessage);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }

            Map<String, String> response = new HashMap<>();
            boolean allSuccess = true;

            // 요청 받은 파일 목록 처리
            for (Map<String, String> request : requests) {
                try {
                    int fileUploadTableIdx = Integer.parseInt(request.get("id"));
                    String file_name = request.get("file_name");
                    String path = request.get("path");

                    // 파일 삭제 시도
                    if (!msFileService.fileDelete(fileUploadTableIdx, file_name, path)) {
                        allSuccess = false;
                        log.error("Failed to delete file with id: {}", fileUploadTableIdx);
                    }
                } catch (NumberFormatException e) {
                    log.error("Invalid file ID format: {}", request.get("id"), e);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Collections.singletonMap("message", "Invalid file ID format"));
                } catch (NullPointerException e) {
                    log.error("Missing file parameters: {}", request, e);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Collections.singletonMap("message", "Missing file parameters"));
                }
            }

            // 전체 성공 여부에 따른 응답
            if (allSuccess) {
                response.put("status", "200");
                response.put("message", "All files deleted successfully");
            } else {
                response.put("status", "404");
                response.put("message", "Some files failed to delete");
            }

            return ResponseEntity.ok(response);
        } catch (NullPointerException e) {
            log.error("Error deleting files: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("message", "Error deleting files"));
        } catch (IllegalArgumentException e){
            log.error("Error deleting files: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("message", "Error deleting files"));
        } catch (Exception e) {
            log.error("Error deleting files: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("message", "Error deleting files"));
        }
    }



}

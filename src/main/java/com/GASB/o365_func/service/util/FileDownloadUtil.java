package com.GASB.o365_func.service.util;

import com.GASB.o365_func.model.dto.MsFileInfoDto;
import com.GASB.o365_func.model.entity.*;
import com.GASB.o365_func.model.mapper.MsFileMapper;
import com.GASB.o365_func.repository.*;
import com.GASB.o365_func.service.message.MessageSender;
import com.GASB.o365_func.tlsh.Tlsh;
import com.GASB.o365_func.tlsh.TlshCreator;
import com.microsoft.graph.requests.GraphServiceClient;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileDownloadUtil {

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Value("{aes.key}")
    private String key;

    private final RestTemplate restTemplate;
    private final StoredFileRepo storedFileRepo;
    private final ActivitiesRepo activitiesRepo;
    private final MessageSender messageSender;
    private final FileUploadTableRepo fileUploadTableRepo;
    private final S3Client s3Client;
    private final MsFileMapper msFileMapper;
    private final MonitoredUsersRepo monitoredUsersRepo;
    private final ScanUtil scanUtil;
    private final FileEncUtil fileEncUtil;


    private static final String HASH_ALGORITHM = "SHA-256";
    private static final Path BASE_PATH = Paths.get("downloads");

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(BASE_PATH);
            log.info("Base path directory created or already exists: {}", BASE_PATH.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to create base path directory: {}", BASE_PATH.toAbsolutePath(), e);
            throw new RuntimeException("Could not create base directory", e);
        }
    }

    @Async("threadPoolTaskExecutor")
    @Transactional
    public CompletableFuture<Void> processAndStoreFile(MsFileInfoDto file, OrgSaaS orgSaaSObject, int workspaceId, String event_type, GraphServiceClient graphClient) {

        return downloadFileAsync(file.getFile_download_url(),file, graphClient)
                .thenApply(fileData -> {
                    try {
                        return handleFileProcessing(file, orgSaaSObject, fileData, workspaceId, event_type);
                    } catch (IOException | NoSuchAlgorithmException e) {
                        throw new RuntimeException("File processing failed", e);
                    }
                })
                .exceptionally(ex -> {
                    log.error("Error processing file: {}", file.getFile_name(), ex);
                    return null;
                });
    }
    @Async("threadPoolTaskExecutor")
    public CompletableFuture<byte[]> downloadFileAsync(String fileUrl, MsFileInfoDto fileData, GraphServiceClient graphClient) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return downloadFileWithSDK(BASE_PATH.resolve(fileData.file_name).toString(), fileData, graphClient);
            } catch (Exception e) {
                log.error("Error downloading file: {}", fileUrl, e);
                throw new RuntimeException("File download failed", e);
            }
        }).exceptionally(ex -> {
            log.error("Async error occurred during file download: {}", fileUrl, ex);
            return null; // 또는 예외 상황에서 반환할 기본값
        });
    }


    private byte[] downloadFileWithSDK(String filePath, MsFileInfoDto file, GraphServiceClient graphClient) {
        try {
            // Microsoft Graph API를 통한 파일 다운로드
            InputStream inputStream;
            if (file.isOneDrive()) {
                inputStream = graphClient.users(file.getFile_owner_id())
                        .drive()
                        .items(file.getFile_id())
                        .content()
                        .buildRequest()
                        .get();
            } else if (!file.isOneDrive()) {
                inputStream = graphClient.sites(file.getSite_id())
                        .drive()
                        .items(file.getFile_id())
                        .content()
                        .buildRequest()
                        .get();
            } else {
                throw new IllegalArgumentException("Unsupported file source.");
            }

            // 절대 경로로 변환하여 출력
            Path absolutePath = Paths.get(filePath).toAbsolutePath();
            log.info("Saving file to absolute path: {}", absolutePath);
            if(absolutePath.getFileName() == null){
                log.error("File name is null");
                throw new IllegalArgumentException("File path must include the file name.");
            }

            // 파일 저장 디렉터리 생성
            Path parentDir = absolutePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            // 바이트 배열로 데이터를 다운로드 및 저장
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                 FileOutputStream fileOutputStream = new FileOutputStream(absolutePath.toString())) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.writeTo(fileOutputStream);

                byte[] fileData = outputStream.toByteArray();
                long downloadedSize = fileData.length;

                log.info("File size: {} bytes", downloadedSize);
                log.info("Download Successful, FileName: {}, File SavePath: {}", file.getFile_name(), absolutePath);

                return fileData;

            } catch (IOException e) {
                log.error("IO error while downloading file: {}", e.getMessage(), e);
                throw new RuntimeException("File download failed", e);
            }

        } catch (IOException e) {
            log.error("IO error while downloading file: {}", e.getMessage(), e);
            throw new RuntimeException("File download failed", e);
        } catch (Exception e) {
            log.error("An unexpected error occurred while downloading the file: {}", e.getMessage(), e);
            throw new RuntimeException("Unexpected error", e);
        }
    }


    public void deleteFileInS3(String filePath) {
        try {
            // 삭제 요청 생성
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(filePath)
                    .build();

            // S3에서 파일 삭제
            s3Client.deleteObject(deleteObjectRequest);
            System.out.println("File deleted successfully from S3: " + key);

        } catch (S3Exception e) {
            // 예외 처리
            System.err.println("Error deleting file from S3: " + e.awsErrorDetails().errorMessage());
        }
    }



    private Void handleFileProcessing(MsFileInfoDto file, OrgSaaS orgSaaSObject, byte[] fileData, int workspaceId, String event_type) throws IOException, NoSuchAlgorithmException {
        String file_name = file.getFile_name();
        log.info("Processing file: {}", file_name);
        log.info("file event type : {}", event_type);

        String hash = calculateHash(fileData);

        // TLSH 계산 시 예외가 발생해도 프로세스를 멈추지 않도록 예외 처리
        String tlsh = null;
        try {
            Tlsh tlshResult = computeTlsHash(fileData);
            if (tlshResult != null) {
                tlsh = tlshResult.toString();
            } else {
                tlsh = "TLSH calculation failed"; // TLSH 계산 실패 시 대체 값
            }
        } catch (Exception e) {
            log.error("Error computing TLSH hash", e);
            tlsh = "TLSH calculation failed";
        }
        log.info("TLSH: {}", tlsh);

        LocalDateTime changeTime = extractChangeTime(event_type);
        String userId = file.getFile_owner_id();

        MonitoredUsers user = monitoredUsersRepo.fineByUserIdAndorgSaaSId(userId, workspaceId).orElse(null);
        if (user == null) return null;

        String saasName = orgSaaSObject.getSaas().getSaasName();
        String orgName = orgSaaSObject.getOrg().getOrgName();

        String filePath = BASE_PATH.resolve(file.getFile_name()).toString();
        String s3Key = getFullPath(file, saasName, orgName, hash);
        String displayPath = createDisplayPath(orgName, saasName, file.file_owner_name, filePath);

        // 다른 작업을 계속 수행
        processAndSaveFileData(file, hash, s3Key, orgSaaSObject, changeTime, event_type, user, displayPath, tlsh, filePath);

        return null;
    }


    private String createDisplayPath(String orgName, String saasName, String owner_name, String filePath) {
        if (filePath != null) {
            return String.format("%s/%s/%s/%s", orgName, saasName, owner_name, filePath);
        }
        return "";
    }

    public static String calculateHash(byte[] fileData) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
        log.info("Hash value : {} ", digest);
        byte[] hash = digest.digest(fileData);

        return bytesToHex(hash);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            hexString.append(hex.length() == 1 ? "0" : "").append(hex);
        }
        return hexString.toString();
    }

    //TLSH 해시 계산
    private Tlsh computeTlsHash(byte[] fileData) {
        if (fileData == null || fileData.length == 0) {
            throw new IllegalArgumentException("fileData cannot be null or empty");
        }

        final int BUFFER_SIZE = 4096;
        TlshCreator tlshCreator = new TlshCreator();

        try (InputStream is = new ByteArrayInputStream(fileData)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = is.read(buf)) != -1) {
                // buf 자체의 null 체크는 불필요, 내부적으로 초기화된 배열
                tlshCreator.update(buf, 0, bytesRead);
            }
        } catch (IOException e) {
            log.error("Error reading file data for TLSH hash calculation", e);
            return null; // TLSH 계산 실패 시 null 반환
        }

        try {
            Tlsh hash = tlshCreator.getHash();
            if (hash == null) {
                log.warn("TLSH hash is null, calculation may have failed");
                return null;
            }
            return hash;
        } catch (IllegalStateException e) {
            log.warn("TLSH not valid; either not enough data or data has too little variance");
            return null; // TLSH 계산 실패 시 null 반환
        }
    }



    private LocalDateTime extractChangeTime(String event_type) {
        LocalDateTime changeTime = null;
        if (event_type.contains(":")) {
            String[] event = event_type.split(":");
            try {
                long timestamp = Long.parseLong(event[1].split("\\.")[0]);
                changeTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());
                log.info("changeTime : {}", changeTime);
            } catch (DateTimeParseException | NumberFormatException e) {
                log.error("Failed to parse event timestamp: {}", event[1], e);
            }
        }
        return changeTime;
    }
    private void processAndSaveFileData(MsFileInfoDto file, String hash, String s3Key, OrgSaaS orgSaaSObject,
                                        LocalDateTime changeTime, String event_type, MonitoredUsers user,
                                        String uploadedChannelPath, String tlsh, String filePath) {

        if (file == null) {
            log.error("Invalid file data: null");
            return;
        }

        StoredFile storedFile = msFileMapper.toStoredFileEntity(file, hash, s3Key);
        if (storedFile == null) {
            log.error("Error creating stored file entity: {}", file.getFile_name());
            return;
        }
        FileUploadTable fileUploadTableObject = msFileMapper.toFileUploadEntity(file, orgSaaSObject, hash, changeTime);
        if (fileUploadTableObject == null) {
            log.error("Error creating file upload entity: {}", file.getFile_name());
            return;
        }
        Activities activity = msFileMapper.toActivityEntity(file, event_type, user, uploadedChannelPath,tlsh);
        if (activity == null) {
            log.error("Error creating activity entity: {}", file.getFile_name());
            return;
        }

        synchronized (this) {
            saveActivity(activity, file.getFile_name());
            saveFileUpload(fileUploadTableObject, file, filePath,s3Key);
            saveStoredFile(storedFile, file.getFile_name());
        }
    }

    private void saveActivity(Activities activity, String file_name) {
        try {
            if (!activitiesRepo.existsBySaasFileIdAndEventTs(activity.getSaasFileId(), activity.getEventTs())) {
                activitiesRepo.save(activity);
                messageSender.sendGroupingMessage(activity.getId());
            } else {
                log.warn("Duplicate activity detected and ignored: {}", file_name);
            }
        } catch (DataIntegrityViolationException e) {
            log.error("Error saving activity: {}", e.getMessage(), e);
        }
    }

    private void saveFileUpload(FileUploadTable fileUploadTableObject, MsFileInfoDto file_data, String file_path, String s3Key) {
        try {
            if (!fileUploadTableRepo.existsBySaasFileIdAndTimestamp(fileUploadTableObject.getSaasFileId(), fileUploadTableObject.getTimestamp())){
                fileUploadTableRepo.save(fileUploadTableObject);
                scanUtil.scanFile(file_data, fileUploadTableObject, file_path, s3Key);
            } else {
                log.warn("Duplicate file upload detected and ignored: {}", file_data.getFile_name());
            }
        } catch (DataIntegrityViolationException e) {
            log.error("Error saving file upload: {}", e.getMessage(), e);
        }
    }

    private void saveStoredFile(StoredFile storedFile, String file_name) {
        try {
            if (!storedFileRepo.existsBySaltedHash(storedFile.getSaltedHash())) {
                try {
                    storedFileRepo.save(storedFile);
                    log.info("File uploaded successfully: {}", file_name);
                } catch (DataIntegrityViolationException e) {
                    log.warn("Duplicate entry detected and ignored: {}", file_name);
                }
            } else {
                log.warn("Duplicate file detected: {}", file_name);
            }
        } catch (DataIntegrityViolationException e) {
            log.error("Error saving file: {}", e.getMessage(), e);
        }
    }

    public String getFullPath(MsFileInfoDto file, String SaaSName, String orgName, String hash) {
        List<String> pathParts = new ArrayList<>();
        if (file.isShared()){
            pathParts.add("shared");
        }
        // /drive/root: 이렇게 나오는거 수정 필요
        pathParts.add(file.getFile_owner_name());

        // 해시값을 경로에 추가
        pathParts.add(hash);

        // 파일 이름을 경로에 추가
        pathParts.add(file.getFile_name());

        // "Drive"를 DriveName으로 변경
//        pathParts.set(pathParts.indexOf("Drive"), DriveName);
        // 드라이브 이름을 맨 앞에 추가
        pathParts.add(0, SaaSName);

        pathParts.add(0, orgName);
        log.info("pathParts : {}", String.join("/", pathParts));
        return String.join("/", pathParts);
    }


}

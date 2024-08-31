package com.GASB.o365_func.service;

import com.GASB.o365_func.model.dto.MsFileInfoDto;
import com.GASB.o365_func.model.entity.*;
import com.GASB.o365_func.model.mapper.MsFileMapper;
import com.GASB.o365_func.repository.*;
import com.GASB.o365_func.tlsh.Tlsh;
import com.GASB.o365_func.tlsh.TlshCreator;
import com.microsoft.graph.models.Drive;
import com.microsoft.graph.models.Request;
import com.microsoft.graph.requests.GraphServiceClient;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.*;
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
import java.util.Optional;
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
    private final WorkSpaceConfigRepo workSpaceRepo;
    private final FileUploadTableRepo fileUploadTableRepo;
    private final S3Client s3Client;
    private final MsFileMapper msFileMapper;
    private final MonitoredUsersRepo monitoredUsersRepo;
    private final ScanUtil scanUtil;


    private static final String HASH_ALGORITHM = "SHA-256";
    private static final Path BASE_PATH = Paths.get("downloads");

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
                return downloadFileWithSDK(BASE_PATH.toString(), fileData, graphClient);
            } catch (Exception e) {
                log.error("Error downloading file: {}", fileUrl, e);
                throw new RuntimeException("File download failed", e);
            }
        }).exceptionally(ex -> {
            log.error("Async error occurred during file download: {}", fileUrl, ex);
            return null; // 또는 예외 상황에서 반환할 기본값
        });
    }


    private byte[] downloadFileWithSDK(String dirPath, MsFileInfoDto file, GraphServiceClient graphClient) {
        try {
            InputStream inputStream;

            // 원드라이브 파일인 경우
            if (file.isOneDrive()) {
                inputStream = graphClient.users(file.getFile_owner_id())
                        .drive()
                        .items(file.getFile_id())
                        .content()
                        .buildRequest()
                        .get();

                // 쉐어포인트 파일인 경우
            } else if (!file.isOneDrive()) {
                inputStream = graphClient.sites(file.getSite_id())  // 쉐어포인트 사이트 ID 사용
                        .drive()
                        .items(file.getFile_id())
                        .content()
                        .buildRequest()
                        .get();
            } else {
                throw new IllegalArgumentException("Unsupported file source.");
            }

            // 바이트 배열로 변환
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            byte[] fileData = outputStream.toByteArray();

            Path filePath = Paths.get(dirPath, file.getFile_name());
            File targetFile = filePath.toFile();

            // 디렉토리가 존재하지 않으면 생성
            if (!targetFile.getParentFile().exists()) {
                targetFile.getParentFile().mkdirs();
            }

            // 파일 저장
            try (FileOutputStream fileOutputStream = new FileOutputStream(targetFile)) {
                fileOutputStream.write(fileData);
            }

            return fileData;

        } catch (Exception e) {
            log.error("Error downloading file: {}", e.getMessage(), e);
            throw new RuntimeException("Error downloading file", e);
        }
    }





    private Void handleFileProcessing(MsFileInfoDto file, OrgSaaS orgSaaSObject, byte[] fileData, int workspaceId, String event_type) throws IOException, NoSuchAlgorithmException {
        String file_name = file.getFile_name();
        log.info("Processing file: {}", file_name);
        log.info("file event type : {}", event_type);

        String hash = calculateHash(fileData);
        Tlsh tlsh = computeTlsHash(fileData);
        log.info(tlsh.toString());

        LocalDateTime changeTime = extractChangeTime(event_type);


//        String workspaceName = getWorkspaceName(workspaceId);
        String workspaceName = "O365 Test";
        String userId = file.getFile_owner_id();
        String uploadedUserName = file.getFile_owner_name();

        MonitoredUsers user = monitoredUsersRepo.fineByUserIdAndorgSaaSId(userId, workspaceId).orElse(null);
        if (user == null) return null;

        String saasName = orgSaaSObject.getSaas().getSaasName();
        String orgName = orgSaaSObject.getOrg().getOrgName();

        String filePath = BASE_PATH.resolve(file.getFile_name()).toString();
        String s3Key = getFullPath(file, saasName, orgName, hash);

        processAndSaveFileData(file, hash, s3Key, orgSaaSObject, changeTime, event_type, user, s3Key, tlsh.toString(), filePath);


        uploadFileToS3(filePath, s3Key);

        return null;
    }

    public static String calculateHash(byte[] fileData) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
        log.info("Hash value : {} ", digest);
        byte[] hash = digest.digest(fileData);

        return bytesToHex(hash);
    }

    private String getWorkspaceName(int workspaceId) {
        return workSpaceRepo.findById(workspaceId).get().getWorkspaceName();
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
    private Tlsh computeTlsHash(byte[] fileData) throws IOException {
        if (fileData == null) {
            throw new IllegalArgumentException("fileData cannot be null");
        }
        final int BUFFER_SIZE = 4096;
        TlshCreator tlshCreator = new TlshCreator();

        try (InputStream is = new ByteArrayInputStream(fileData)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = is.read(buf)) != -1) {
                tlshCreator.update(buf, 0, bytesRead);
            }
        } catch (IOException e) {
            throw new IOException("Error computing TLSH hash", e);
        }

        return tlshCreator.getHash();
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

    private String formatUploadedChannelPath(String orgName, String saasName, String workspaceName, String channelName, String uploadedUserName) {
        return String.format("%s/%s/%s/%s/%s", orgName, saasName, workspaceName, channelName, uploadedUserName);
    }

    private String formatS3Key(String orgName, String saasName, String workspaceName, String channelName, String hash, String title) {
        return String.format("%s/%s/%s/%s/%s/%s", orgName, saasName, workspaceName, channelName, hash, title);
    }

    private void processAndSaveFileData(MsFileInfoDto file, String hash, String s3Key, OrgSaaS orgSaaSObject,
                                        LocalDateTime changeTime, String event_type, MonitoredUsers user,
                                        String uploadedChannelPath, String tlsh, String filePath) {
        StoredFile storedFile = msFileMapper.toStoredFileEntity(file, hash, s3Key);
        FileUploadTable fileUploadTableObject = msFileMapper.toFileUploadEntity(file, orgSaaSObject, hash, changeTime);
        Activities activity = msFileMapper.toActivityEntity(file, event_type, user, uploadedChannelPath,tlsh);

        synchronized (this) {
            saveActivity(activity, file.getFile_name());
            saveFileUpload(fileUploadTableObject, file, filePath);
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

    private void saveFileUpload(FileUploadTable fileUploadTableObject, MsFileInfoDto file_data, String file_path) {
        try {
            if (!fileUploadTableRepo.existsBySaasFileIdAndTimestamp(fileUploadTableObject.getSaasFileId(), fileUploadTableObject.getTimestamp())){
                fileUploadTableRepo.save(fileUploadTableObject);
                scanUtil.scanFile(file_data, fileUploadTableObject, file_path);
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
                    messageSender.sendMessage(storedFile.getId());
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


    private void uploadFileToS3(String filePath, String s3Key) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            s3Client.putObject(putObjectRequest, Paths.get(filePath));
            log.info("File uploaded successfully to S3: {}", s3Key);
        } catch (Exception e) {
            log.error("Error uploading file to S3: {}", e.getMessage(), e);
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

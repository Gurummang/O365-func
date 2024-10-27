package com.GASB.o365_func.service.util;

import com.GASB.o365_func.model.dto.MsFileInfoDto;
import com.GASB.o365_func.model.entity.FileUploadTable;
import com.GASB.o365_func.model.entity.TypeScan;
import com.GASB.o365_func.repository.TypeScanRepo;
import com.GASB.o365_func.service.enumset.HeaderSignature;
import com.GASB.o365_func.service.enumset.MimeType;
import com.GASB.o365_func.service.message.MessageSender;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.Null;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScanUtil {


    private final TypeScanRepo typeScanRepo;
    private final MessageSender messageSender;
    private final S3Client s3Client;
    private final FileEncUtil fileEncUtil;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Async
    public void scanFile(MsFileInfoDto fileData, FileUploadTable fileUploadTableObject, String filePath, String s3Key){
        try{
            File inputFile = new File(filePath);
            if (!inputFile.exists() || !inputFile.isFile()){
                log.error("Invalid file path: {}", filePath);
                return;
            }

            String fileExtension = extractFileExtensionByFileName(fileData.getFile_name());
            String expectedFileTypeByExtension = MimeType.getMimeTypeByExtension(fileExtension);

            String mimeType = fileData.getFile_mimetype();

            String fileSignature = null;

            boolean isMatched;

            if (fileExtension.equals("txt")) {
                // txt 파일의 경우 시그니처가 없으므로 MIME 타입만으로 검증
                isMatched = mimeType.equals(expectedFileTypeByExtension);
                addData(fileUploadTableObject, isMatched, mimeType, "unknown", fileExtension);
            } else {
                fileSignature = extractSignature(inputFile, fileData,fileExtension);
                if (fileSignature == "unknown"){
                    // 확장자와 MIME타입만 검사함
                    isMatched = checkWithoutSignature(mimeType, expectedFileTypeByExtension, fileExtension);
                    addData(fileUploadTableObject, isMatched, mimeType, "unknown", fileExtension);
                }
                if (fileSignature == null || fileSignature.isEmpty()) {
                    // 확장자와 MIME 타입만 존재하는 경우
                    isMatched = checkWithoutSignature(mimeType, expectedFileTypeByExtension, fileExtension);
                    addData(fileUploadTableObject, isMatched, mimeType, "unknown", fileExtension);
                } else {
                    // MIME 타입, 확장자, 시그니처가 모두 존재하는 경우
                    isMatched = checkAllType(mimeType, fileExtension, fileSignature, expectedFileTypeByExtension);
                    addData(fileUploadTableObject, isMatched, mimeType, fileSignature, fileExtension);
                }

                messageSender.sendMessage(fileUploadTableObject.getId());

                uploadFileToS3(filePath,s3Key);

            }
        } catch (IllegalArgumentException e){
            log.error("Error scanning file: {}", e.getMessage(), e);
        } catch (NullPointerException e){
            log.error("Error scanning file: {}", e.getMessage(), e);
        } catch (Exception e){
            log.error("Error scanning file: {}", e.getMessage(), e);
        }
    }

    @Async
    @Transactional
    protected void addData(FileUploadTable fileUploadTableObject, boolean correct, String mimeType, String signature, String extension) {
        if (fileUploadTableObject == null) {
            log.error("Invalid file upload object: null");
            throw new IllegalArgumentException("fileUploadTableObject cannot be null");
        }

        if (fileUploadTableObject.getId() == null) {
            log.error("Invalid file upload object ID: null for object: {}", fileUploadTableObject);
            throw new IllegalArgumentException("fileUploadTableObject ID cannot be null");
        }

        TypeScan typeScan = TypeScan.builder()
                .file_upload(fileUploadTableObject)
                .correct(correct)
                .mimetype(mimeType)
                .signature(signature)
                .extension(extension)
                .build();
        typeScanRepo.save(typeScan);
    }


    private String extractFileExtensionByFileName(String file_name){
        return file_name.substring(file_name.lastIndexOf(".")+1);
    }

    private String extractSignature(File file, MsFileInfoDto fileData, String fileExtension) {
        if (fileExtension == null || fileExtension.isEmpty()) {
            log.error("Invalid file extension: {}", fileExtension);
            return null; // 기본값으로 "unknown"을 반환
        }

        int signatureLength = HeaderSignature.getSignatureLengthByExtension(fileExtension);
        if (signatureLength == 0) {
            log.info("No signature length for extension: {}", fileExtension);
            return "unknown";
        }

        if (fileData.getFile_size() < signatureLength) {
            log.error("File data is smaller than the expected signature length");
            return "unknown";
        }

        byte[] signatureBytes = new byte[signatureLength];

        try (FileInputStream fis = new FileInputStream(file)) {
            int bytesRead = fis.read(signatureBytes);
            if (bytesRead < signatureLength) {
                log.error("Could not read the complete file signature");
                return "unknown";
            }
        } catch (NullPointerException e) {
            log.error("Error reading file signature: {}", e.getMessage(), e);
            return null;
        } catch (IOException e){
            log.error("Error reading file signature: {}", e.getMessage(), e);
            return null;
        } catch (Exception e) {
            log.error("Error reading file signature: {}", e.getMessage(), e);
            return null;
        }

        StringBuilder sb = new StringBuilder(signatureLength * 2);
        for (byte b : signatureBytes) {
            sb.append(String.format("%02X", b));
        }

        String signatureHex = sb.toString();
        String detectedExtension = HeaderSignature.getExtensionBySignature(signatureHex, fileExtension);
        log.info("Detected extension for signature {}: {}", signatureHex, detectedExtension);

        return detectedExtension;
    }

    private boolean checkAllType(String mimeType, String extension, String signature, String expectedMimeType) {
        log.info("Checking all types: mimeType={}, extension={}, signature={}, expectedMimeType={}", mimeType, extension, signature, expectedMimeType);
        return mimeType.equals(expectedMimeType) &&
                MimeType.mimeMatch(mimeType, signature) &&
                MimeType.mimeMatch(mimeType, extension);
    }

    private boolean checkWithoutSignature(String mimeType, String expectedMimeType, String extension) {
        return mimeType.equals(expectedMimeType) &&
                MimeType.mimeMatch(mimeType, extension);
    }


    private void uploadFileToS3(String filePath, String s3Key) {

        //암호화 진행
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();
            // 암호화한 파일을 업로드
            s3Client.putObject(putObjectRequest, fileEncUtil.encryptAndSaveFile(filePath));
            log.info("File uploaded successfully to S3: {}", s3Key);
        } catch (RuntimeException e) {
            log.error("Error uploading file to S3: {}", e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            deleteFileInLocal(filePath);
        }
    }
    public void deleteFileInLocal(String filePath) {
        try {
            // 파일 경로를 Path 객체로 변환
            Path path = Paths.get(filePath);

            // 파일 삭제
            Files.delete(path);
            log.info("File deleted successfully from local filesystem: {}", filePath);

        } catch (IOException e) {
            // 파일 삭제 중 예외 처리
            log.info("Error deleting file from local filesystem: {}" , e.getMessage());
        }
    }

}

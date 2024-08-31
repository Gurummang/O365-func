package com.GASB.o365_func.service;

import com.GASB.o365_func.model.dto.MsFileInfoDto;
import com.GASB.o365_func.model.entity.FileUploadTable;
import com.GASB.o365_func.model.entity.TypeScan;
import com.GASB.o365_func.repository.TypeScanRepo;
import com.GASB.o365_func.service.enumset.HeaderSignature;
import com.GASB.o365_func.service.enumset.MimeType;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScanUtil {


    private final TypeScanRepo typeScanRepo;

    @Async
    public void scanFile(MsFileInfoDto fileData, FileUploadTable fileUploadTableObject, String filePath){
        try{
            File inputFile = new File(filePath);
            if (!inputFile.exists() || !inputFile.isFile()){
                log.error("Invalid file path: {}", filePath);
                return;
            }

            String fileExtension = extractFileExtensionByFileName(fileData.getFile_name());
            String expectedFileTypeByExtension = MimeType.getMimeTypeByExtension(fileExtension);

            String mimeType = fileData.getFile_type();

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
            }




        } catch (Exception e){
            log.error("Error occurred while scanning file: {}", fileData.getFile_name(), e);
        }
    }

    @Async
    @Transactional
    protected void addData(FileUploadTable fileUploadTableObject, boolean correct, String mimeType, String signature, String extension) {
        if (fileUploadTableObject == null || fileUploadTableObject.getId() == null) {
            log.error("Invalid file upload object: {}, {}", fileUploadTableObject, fileUploadTableObject.getId());

            throw new IllegalArgumentException("Invalid file upload object");
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
        } catch (Exception e) {
            log.error("Error reading file signature", e);
            return "unknown";
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

}

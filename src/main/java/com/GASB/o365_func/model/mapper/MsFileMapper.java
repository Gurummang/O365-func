package com.GASB.o365_func.model.mapper;


import com.GASB.o365_func.model.dto.MsFileInfoDto;
import com.GASB.o365_func.model.entity.*;
import com.GASB.o365_func.repository.MonitoredUsersRepo;
import com.GASB.o365_func.service.enumset.MimeType;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.james.mime4j.dom.datetime.DateTime;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@Slf4j
@RequiredArgsConstructor
public class MsFileMapper {


    @Value("${aws.s3.bucket}")
    private String bucketName;

    private final MonitoredUsersRepo monitoredUsersRepo;

    private final ZoneId zoneId = ZoneId.of("Asia/Seoul");

    public MsFileInfoDto toOneDriveEntity(DriveItem item){
        log.info("DriveItem: {}", item);

        // 파일 정보와 MimeType에 대한 null 체크
        String mimeType = Optional.ofNullable(item.file)
                .map(file -> file.mimeType)
                .orElse("text/plain");

        OffsetDateTime utcTime = item.createdDateTime;
        log.info("utcTime : {}", utcTime);
        LocalDateTime utcTimeToLocal = Objects.requireNonNull(utcTime)
                .atZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
        LocalDateTime kstTime = Objects.requireNonNull(utcTime).atZoneSameInstant(zoneId).toLocalDateTime();
        log.info("kstTime : {}", kstTime);
        return MsFileInfoDto.builder()
                .file_id(item.id)
                .file_name(item.name)
                .file_type(MimeType.getExtensionByMimeType(mimeType)) // txt 파일로 변환
                .file_mimetype(mimeType)
                .file_download_url(Optional.ofNullable(item.additionalDataManager().get("@microsoft.graph.downloadUrl"))
                        .map(Object::toString)
                        .orElse(null)) // 다운로드 URL null 체크
                .file_size(item.size)
                .file_owner_id(Optional.ofNullable(item.createdBy)
                        .map(createdBy -> createdBy.user)
                        .map(user -> user.id)
                        .orElse(null)) // 파일 소유자 ID null 체크
                .file_owner_name(Optional.ofNullable(item.createdBy)
                        .map(createdBy -> createdBy.user)
                        .map(user -> user.displayName)
                        .orElse(null)) // 파일 소유자 이름 null 체크
                .file_created_time(utcTimeToLocal) // 파일 생성 시간 null 체크
                .file_path(Optional.ofNullable(item.parentReference)
                        .map(reference -> reference.path)
                        .orElse(null)) // 경로 null 체크
                .isOneDrive(true)
                .build();
    }



    public MsFileInfoDto OneDriveChangeEvent(DriveItem item){
        log.info("DriveItem: {}", item);

        // 파일 정보와 MimeType에 대한 null 체크
        String mimeType = Optional.ofNullable(item.file)
                .map(file -> file.mimeType)
                .orElse("text/plain");
        OffsetDateTime utcTime = item.lastModifiedDateTime;
        LocalDateTime utcTimeToLocal = Objects.requireNonNull(utcTime)
                .atZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();

        LocalDateTime kstTime = Objects.requireNonNull(utcTime).atZoneSameInstant(zoneId).toLocalDateTime();
        log.info("KST Time : {} ", kstTime);
        return MsFileInfoDto.builder()
                .file_id(item.id)
                .file_name(item.name)
                .file_type(MimeType.getExtensionByMimeType(mimeType)) // MimeType을 파일 확장자로 변환
                .file_mimetype(mimeType)
                .file_download_url(Optional.ofNullable(item.additionalDataManager().get("@microsoft.graph.downloadUrl"))
                        .map(Object::toString)
                        .orElse(null)) // 다운로드 URL null 체크
                .file_size(item.size)
                .file_owner_id(Optional.ofNullable(item.createdBy)
                        .map(createdBy -> createdBy.user)
                        .map(user -> user.id)
                        .orElse(null)) // 파일 소유자 ID null 체크
                .file_owner_name(Optional.ofNullable(item.createdBy)
                        .map(createdBy -> createdBy.user)
                        .map(user -> user.displayName)
                        .orElse(null)) // 파일 소유자 이름 null 체크
                .file_created_time(utcTimeToLocal)// 마지막 수정 시간을 서울 시간대로 변환하여 설정
                .file_path(Optional.ofNullable(item.parentReference)
                        .map(reference -> reference.path)
                        .orElse(null)) // 경로 null 체크
                .isOneDrive(true)
                .build();
    }

    public MsFileInfoDto toSharePointEntity(DriveItem item){
        log.info("DriveItem: {}", item);

        // 파일 정보와 MimeType에 대한 null 체크
        String mimeType = Optional.ofNullable(item.file)
                .map(file -> file.mimeType)
                .orElse("text/plain");
        OffsetDateTime utcTime = item.createdDateTime;
        LocalDateTime utcTimeToLocal = Objects.requireNonNull(utcTime)
                .atZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
        LocalDateTime kstTime = Objects.requireNonNull(utcTime).atZoneSameInstant(zoneId).toLocalDateTime();


        return MsFileInfoDto.builder()
                .file_id(item.id)
                .file_name(item.name)
                .file_type(MimeType.getExtensionByMimeType(mimeType)) // MimeType을 파일 확장자로 변환
                .file_mimetype(mimeType)
                .file_download_url(Optional.ofNullable(item.additionalDataManager().get("@microsoft.graph.downloadUrl"))
                        .map(Object::toString)
                        .orElse(null)) // 다운로드 URL null 체크
                .file_size(item.size)
                .file_owner_id(Optional.ofNullable(item.createdBy)
                        .map(createdBy -> createdBy.user)
                        .map(user -> user.id)
                        .orElse(null)) // 파일 소유자 ID null 체크
                .file_owner_name(Optional.ofNullable(item.createdBy)
                        .map(createdBy -> createdBy.user)
                        .map(user -> user.displayName)
                        .orElse(null)) // 파일 소유자 이름 null 체크
                .file_created_time(utcTimeToLocal)
                .file_path(Optional.ofNullable(item.parentReference)
                        .map(reference -> reference.path)
                        .orElse(null)) // 경로 null 체크
                .site_id(Optional.ofNullable(item.parentReference)
                        .map(reference -> reference.siteId)
                        .orElse(null)) // Site ID null 체크
                .build();
    }



    public StoredFile toStoredFileEntity(MsFileInfoDto file, String hash, String filePath) {
        if (file == null) {
            return null;
        }
        return StoredFile.builder()
                .type(file.getFile_type())
                .size(file.getFile_size().intValue())
                .savePath(bucketName + "/" + filePath)
                .saltedHash(hash)
                .build();
    }
    public List<StoredFile> toStoredFileEntity(List<MsFileInfoDto> files, List<String> hashes, List<String> filePaths) {
        return IntStream.range(0, files.size())
                .mapToObj(i -> toStoredFileEntity(files.get(i), hashes.get(i), filePaths.get(i)))
                .collect(Collectors.toList());
    }

    public FileUploadTable toFileUploadEntity(MsFileInfoDto file, OrgSaaS orgSaas, String hash, LocalDateTime timestamp) {
        if (file == null) {
            return null;
        }
        log.info("file.getFile_created_time() : {}", file.getFile_created_time());
        return FileUploadTable.builder()
                .orgSaaS(orgSaas)
                .saasFileId(file.getFile_id())
                .hash(hash)
                .timestamp(file.getFile_created_time())
                .build();
    }


    public Activities toActivityEntity(MsFileInfoDto file, String eventType, MonitoredUsers user, String channel, String tlsh) {
        if (file == null) {
            return null;
        }

        // eventType null 체크
        if (eventType == null || eventType.isEmpty()) {
            eventType = "file_upload";
        }
        log.info("created_activity_time : {}", file.getFile_created_time());
        return Activities.builder()
                .user(user)
                .eventType(eventType)
                .saasFileId(file.getFile_id())
                .fileName(file.getFile_name())
                .eventTs(file.getFile_created_time())  // eventTs가 null일 수 있음에 유의
                .uploadChannel(channel)
                .tlsh(tlsh == null ? "not enough data or data has too little variance" : tlsh)
                .build();
    }

    public Activities toActivityEntitiyForDeleteEvent(String file_id, String eventType, MonitoredUsers user, String file_name, long timestamp){
        return Activities.builder()
                .user(user)
                .eventType(eventType)
                .saasFileId(file_id)
                .fileName(file_name)
                .eventTs(LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.of("Asia/Seoul")))
                .uploadChannel("deleted")
                .build();
    }

    public Activities copyActivityEntity(Activities activity, long timestamp) {
        return Activities.builder()
                .user(activity.getUser())
                .eventType("file_delete")
                .saasFileId(activity.getSaasFileId())
                .fileName(activity.getFileName())
                .eventTs(LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.of("Asia/Seoul")))
                .uploadChannel(activity.getUploadChannel())
                .build();
    }
}

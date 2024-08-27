package com.GASB.o365_func.model.mapper;


import com.GASB.o365_func.model.dto.MsFileInfoDto;
import com.GASB.o365_func.model.entity.*;
import com.GASB.o365_func.repository.MonitoredUsersRepo;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.james.mime4j.dom.datetime.DateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@Slf4j
@RequiredArgsConstructor
public class MsFileMapper {


    @Value("${aws.s3.bucket}")
    private String bucketName;

    private final MonitoredUsersRepo monitoredUsersRepo;


    public MsFileInfoDto toEntity(DriveItem item){
        return MsFileInfoDto.builder()
                .file_id(item.id)
                .file_name(item.name)
                .file_type(item.file.mimeType) //이거는 mimeType이라서 좀 수정할 필요있음
                .file_download_url(item.additionalDataManager().get("@microsoft.graph.downloadUrl").toString())
                .file_size(item.size)
                .file_owner_id(item.createdBy.user.id)
                .file_owner_name(item.createdBy.user.displayName)
                .file_created_time(Objects.requireNonNull(item.createdDateTime).toLocalDateTime().atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime())
                .file_path(Objects.requireNonNull(item.parentReference).path)
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
        return FileUploadTable.builder()
                .orgSaaS(orgSaas)
                .saasFileId(file.getFile_id())
                .hash(hash)
                .timestamp(file.getFile_created_time())
                .build();
    }


    public Activities toActivityEntity(MsFileInfoDto file, String eventType, MonitoredUsers user, String channel) {
        if (file == null) {
            return null;
        }

        // eventType null 체크
        if (eventType == null || eventType.isEmpty()) {
            eventType = "file_upload";
        }

        return Activities.builder()
                .user(user)
                .eventType(eventType)
                .saasFileId(file.getFile_id())
                .fileName(file.getFile_name())
                .eventTs(file.getFile_created_time())  // eventTs가 null일 수 있음에 유의
                .uploadChannel(channel)
                .tlsh("test")
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

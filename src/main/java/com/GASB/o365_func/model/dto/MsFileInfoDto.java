package com.GASB.o365_func.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MsFileInfoDto {
    public String file_id;
    public String file_name;
    public String file_type;
    public String file_mimetype;
    public String file_download_url;
    public Long file_size;
    public String file_owner_id;
    public String file_owner_name;
    public LocalDateTime file_created_time;
    public String file_path;
    public boolean isShared = false;
    public String site_id = null;
    public boolean isOneDrive = false;
}

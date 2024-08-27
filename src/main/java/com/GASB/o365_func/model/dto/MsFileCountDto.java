package com.GASB.o365_func.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MsFileCountDto {
    private int totalFiles;
    private int sensitiveFiles;
    private int maliciousFiles;
    private int connectedAccounts;
}

package com.GASB.o365_func.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MsFileSharedEventDto {
    private String from;
    private String event;
    private String saas;
    private String fileId;
}

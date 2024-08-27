package com.GASB.o365_func.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class MsUserListDto {
    private String id;
    private String displayName;
    private String mail;
    private LocalDateTime activeDate;

    @Builder
    public MsUserListDto(String id, String displayName, String mail, LocalDateTime activeDate) {
        this.id = id;
        this.displayName = displayName;
        this.mail = mail;
        this.activeDate = activeDate;
    }
}

package com.GASB.o365_func.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class FileGroup {

    @Id
    private Long id;

    @OneToOne
    @JoinColumn(name = "id", referencedColumnName = "id")
    private Activities activities;

    @Column(columnDefinition = "TEXT")
    private String groupName;

    @Column(name = "group_type")
    private String groupType;

    public FileGroup(Long id, String groupName, String groupType) {
        this.id = id;
        this.groupName = groupName;
        this.groupType = groupType;
    }
}

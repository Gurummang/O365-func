package com.GASB.o365_func.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "ms_delta_link")
public class MsDeltaLink {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "ms_user_id", nullable = false, referencedColumnName = "id")
    private MonitoredUsers monitoredUsers;

    @Column(name = "delta_link", columnDefinition = "TEXT")
    private String deltaLink;
}

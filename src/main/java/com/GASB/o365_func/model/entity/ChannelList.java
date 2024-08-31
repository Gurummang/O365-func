package com.GASB.o365_func.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Table(name = "channel_list")
public class ChannelList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "org_saas_id", nullable = false, referencedColumnName = "id")
    private OrgSaaS orgSaas;

    @Column(name = "channel_id", nullable = false, unique = true, length = 100)
    private String channelId;

    @Column(name="channel_name", nullable = false, length = 100)
    private String channelName;
}

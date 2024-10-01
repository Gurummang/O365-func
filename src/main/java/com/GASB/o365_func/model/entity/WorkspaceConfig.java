package com.GASB.o365_func.model.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "workspace_config")
public class WorkspaceConfig {
    @Id
    @Column(name = "id", nullable = false)
    private int id;

    @Column(name = "workspace_name", nullable = false, length = 100)
    private String workspaceName;

    @Column(name = "saas_admin_email", nullable = false, length = 100)
    private String saasAdminEmail;

    @Column(name = "token", nullable = false, length = 100)
    private String token;

    @Column(name = "webhook")
    private String webhook;

    @Column(name = "alias")
    private String alias;

    @Column(name = "register_date", nullable = false)
    private Timestamp registerDate;

    @Column(name = "refresh_token")
    private String refreshToken;

    @OneToOne
    @MapsId
    @JoinColumn(name = "id")
    private OrgSaaS orgSaas;

    @Builder
    public WorkspaceConfig(int id, String workspaceName, String saasAdminEmail, String token, String webhook, String alias, Timestamp registerDate, String refreshToken, OrgSaaS orgSaas) {
        this.id = id;
        this.workspaceName = workspaceName;
        this.saasAdminEmail = saasAdminEmail;
        this.token = token;
        this.webhook = webhook;
        this.alias = alias;
        this.registerDate = registerDate;
        this.refreshToken = refreshToken;
        this.orgSaas = orgSaas;
    }
}

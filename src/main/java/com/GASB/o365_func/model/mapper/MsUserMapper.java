package com.GASB.o365_func.model.mapper;

import com.GASB.o365_func.model.dto.MsUserListDto;
import com.GASB.o365_func.model.entity.MonitoredUsers;
import com.GASB.o365_func.model.entity.OrgSaaS;
import com.GASB.o365_func.repository.OrgSaaSRepo;
import com.microsoft.graph.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MsUserMapper {

    private final OrgSaaSRepo orgSaasRepository;
    @Autowired
    public MsUserMapper(OrgSaaSRepo orgSaasRepository) {
        this.orgSaasRepository = orgSaasRepository;
    }

    public static MsUserListDto toDto(User user) {
        return MsUserListDto.builder()
                .id(user.id)
                .displayName(user.displayName)
                .mail(user.mail)
                .activeDate(null)
                .build();
    }

    public MonitoredUsers toEntity(User user, int orgSaaSId) {
        if (user == null) {
            return null;
        }

        OrgSaaS orgSaaS = orgSaasRepository.findById(orgSaaSId)
                .orElseThrow(() -> new IllegalArgumentException("OrgSaas not found with id: " + orgSaaSId));

        return MonitoredUsers.builder()
                .orgSaaS(orgSaaS)
                .userId(user.id)
                .userName(user.displayName)
                .email(user.mail)
                .build();
    }
}

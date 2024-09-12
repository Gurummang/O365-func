package com.GASB.o365_func.repository;


import com.GASB.o365_func.model.entity.OrgSaaS;
import com.GASB.o365_func.model.entity.Saas;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrgSaaSRepo extends JpaRepository<OrgSaaS, Integer> {
//    OrgSaaS findByOrgId(String orgId);

    @Query("SELECT os.saas.id FROM OrgSaaS os WHERE os.id = :org_saas_id")
    int findSaaSIdById(@Param("org_saas_id") int org_saas_id);

}

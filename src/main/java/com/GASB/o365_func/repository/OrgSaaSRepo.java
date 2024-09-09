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

    @Query("SELECT os FROM OrgSaaS os WHERE os.id = :id")
    Optional<OrgSaaS> findById(@Param("id") Long id);
    Optional<OrgSaaS> findBySpaceId(String spaceId);
    Optional<OrgSaaS> findByOrgIdAndSpaceId(int orgId, String spaceId);
    Optional<OrgSaaS> findByOrgIdAndSaas(int orgId, Saas saas);
    List<OrgSaaS> findAllByOrgIdAndSaas(int orgId, Saas saas);
    Optional<OrgSaaS> findBySpaceIdAndOrgId(String spaceId, int orgId);


    boolean existsBySpaceId(String spaceId);
}

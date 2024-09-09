package com.GASB.o365_func.repository;

import com.GASB.o365_func.model.entity.MsDeltaLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface MsDeltaLinkRepo extends JpaRepository<MsDeltaLink, Integer> {

    @Query("SELECT m FROM MsDeltaLink m WHERE m.monitoredUsers.id = :userId")
    Optional<MsDeltaLink> findByUserId(@Param("userId") String userId);

    @Query("SELECT m.deltaLink FROM MsDeltaLink m WHERE m.monitoredUsers.id = :userId")
    Optional<String> findDeltaLinkByUserId(@Param("userId") int userId);

    @Query("SELECT EXISTS(SELECT 1 FROM MsDeltaLink m WHERE m.monitoredUsers.id = :userId)")
    boolean existsByMonitoredUsers_Id(int userId);

    @Transactional
    @Modifying
    @Query("UPDATE MsDeltaLink m SET m.deltaLink = :deltaLink WHERE m.monitoredUsers.id = :userId")
    void updateDeltaLink(@Param("deltaLink") String deltaLink, @Param("userId") int userId);


}

package com.GASB.o365_func.repository;


import com.GASB.o365_func.model.entity.Activities;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ActivitiesRepo extends JpaRepository<Activities, Long> {

    @Query("SELECT ac.user.userId FROM Activities ac WHERE ac.saasFileId = :file_id")
    Optional<String> findUserIdByFileId(@Param("file_id")String file_id);

    @Query("SELECT COUNT(a) > 0 FROM Activities a WHERE a.saasFileId = :saasFileId AND a.eventTs = :eventTs")
    boolean existsBySaasFileIdAndEventTs(@Param("saasFileId") String saasFileId, @Param("eventTs") LocalDateTime eventTs);

    @Query("SELECT distinct a.user.userId FROM Activities a WHERE a.saasFileId = :fileId AND a.eventType = 'file_upload'")
    Optional<String> findUserBySaasFileId(@Param("fileId") String fileId);


    @Query("SELECT distinct a.fileName FROM Activities a WHERE a.saasFileId = :fileId")
    Optional<String> findFileNamesBySaasFileId(@Param("fileId")String fileId);

    @Query("SELECT a FROM Activities a JOIN FileUploadTable fu ON a.saasFileId =fu.saasFileId WHERE a.saasFileId = :saasFileId AND a.eventType != 'file_delete' AND fu.deleted = false ORDER BY a.eventTs DESC LIMIT 1")
    Optional<Activities> findBySaasFileId(@Param("saasFileId") String saasFileId);

    @Query("SELECT a FROM Activities a WHERE a.saasFileId = :saasFileId ORDER BY a.eventTs DESC LIMIT 1")
    Optional<Activities> findRecentBySaasFileId(@Param("saasFileId") String saasFileId);

    @Query("SELECT EXISTS (SELECT 1 FROM Activities a WHERE a.saasFileId = :saasFileId AND a.eventType = 'file_delete')")
    boolean existsAlreadyDeleteFileBySaasFileId(@Param("saasFileId") String saasFileId);

}

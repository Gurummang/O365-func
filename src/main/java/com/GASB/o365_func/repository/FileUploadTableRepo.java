package com.GASB.o365_func.repository;



import com.GASB.o365_func.model.dto.MsRecentFileDTO;
import com.GASB.o365_func.model.entity.FileUploadTable;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public interface FileUploadTableRepo extends JpaRepository<FileUploadTable, Long> {
    @Query("SELECT o.id FROM FileUploadTable fu " +
            "JOIN fu.orgSaaS os " +
            "JOIN os.org o "+
            "WHERE fu.id = :fileId ")
    Optional<Long> findOrgIdByFileId(@Param("fileId") long fileId);

    @Query("SELECT f.hash FROM FileUploadTable f WHERE f.saasFileId = :saasFileId")
    String findHashBySaasFileId(@Param("saasFileId") String saasFileId);

    @Query("SELECT fu FROM FileUploadTable fu " +
            "JOIN OrgSaaS os ON fu.orgSaaS.id = os.id " +
            "WHERE fu.deleted = false AND os.org.id = :orgId")
    List<FileUploadTable> findAllByOrgId(@Param("orgId") long orgId);

    @Query("SELECT COUNT(fu.id) FROM FileUploadTable fu JOIN OrgSaaS os ON fu.orgSaaS.id = os.id WHERE fu.deleted = false AND os.org.id = :orgId")
    Long countFileByOrgId(@Param("orgId") Long orgId);

    @Query("SELECT SUM(sf.size) FROM FileUploadTable fu " +
            "JOIN StoredFile sf ON fu.hash = sf.saltedHash " +
            "JOIN OrgSaaS os ON fu.orgSaaS.id = os.id " +
            "WHERE fu.deleted = false AND os.org.id = :orgId")
    Long getTotalSizeByOrgId(@Param("orgId") long orgId);

    @Query("SELECT COUNT(fu) " +
            "FROM FileUploadTable fu " +
            "JOIN fu.orgSaaS os " +
            "LEFT JOIN fu.storedFile sf " +
            "LEFT JOIN sf.vtReport vr " +
            "WHERE fu.deleted = false AND vr.threatLabel != 'none' AND os.org.id = :orgId")
    int countVtMalwareByOrgId(@Param("orgId") Long orgId);

//    @Query("SELECT new com.hackathon3.grummang_hack.model.dto.file.TotalTypeDto(sf.type, COUNT(sf)) " +
//            "FROM FileUploadTable fu " +
//            "JOIN fu.storedFile sf " +
//            "JOIN fu.orgSaaS os " +
//            "WHERE os.org.id = :orgId AND fu.deleted = false " +
//            "GROUP BY sf.type")
//    List<TotalTypeDto> findFileTypeDistributionByOrgId(@Param("orgId") Long orgId);

    @Query("SELECT fu.timestamp AS date, SUM(sf.size) AS totalSize, COUNT(fu) AS fileCount " +
            "FROM FileUploadTable fu " +
            "JOIN fu.storedFile sf " +
            "JOIN fu.orgSaaS os " +
            "WHERE os.org.id = :orgId AND fu.timestamp BETWEEN :startDate AND :endDate " +
            "GROUP BY fu.timestamp")
    List<Object[]> findStatistics(
            @Param("orgId") long orgId,
            @Param("startDate") LocalDateTime startDateTime,
            @Param("endDate") LocalDateTime endDateTime
    );

    @Query("SELECT MIN(f.timestamp) FROM FileUploadTable f WHERE f.orgSaaS.id = :orgSaaSId AND f.saasFileId = :saasFileId")
    LocalDateTime findEarliestUploadTsByOrgSaaS_IdAndSaasFileId(@Param("orgSaaSId") long orgSaaSId, @Param("saasFileId") String saasFileId);

    // 특정 orgSaaSId와 saasFileId에 대해 가장 최근의 hash를 가져오는 쿼리
    @Query("SELECT f.hash FROM FileUploadTable f WHERE f.orgSaaS.id = :orgSaaSId AND f.saasFileId = :saasFileId AND f.timestamp = (SELECT MAX(f2.timestamp) FROM FileUploadTable f2 WHERE f2.orgSaaS.id = :orgSaaSId AND f2.saasFileId = :saasFileId)")
    String findLatestHashBySaasFileId(@Param("orgSaaSId") long orgSaaSId, @Param("saasFileId") String saasFileId);

    @Query("SELECT f.hash FROM FileUploadTable f WHERE f.orgSaaS.id = :orgSaaSId AND f.saasFileId = :saasFileId AND f.timestamp = :eventTs")
    String findHashByOrgSaaS_IdAndSaasFileId(@Param("orgSaaSId") long orgSaaSId, @Param("saasFileId") String saasFileId, @Param("eventTs") LocalDateTime eventTs);

    @Query("SELECT f FROM FileUploadTable f WHERE f.timestamp = :timestamp AND f.hash = :hash")
    Optional<FileUploadTable> findByTimestampAndHash(@Param("timestamp") LocalDateTime timestamp, @Param("hash") String hash);

    @Query("SELECT new com.GASB.o365_func.model.dto.MsRecentFileDTO(a.fileName, u.userName, sf.type, fu.timestamp) " +
            "FROM FileUploadTable fu " +
            "JOIN OrgSaaS os ON fu.orgSaaS.id = os.id " +
            "JOIN Activities a ON fu.saasFileId = a.saasFileId " +
            "JOIN StoredFile sf ON fu.hash = sf.saltedHash " +
            "JOIN MonitoredUsers u ON a.user.id = u.id " +
            "WHERE os.org.id = :orgId AND os.saas.id = :saasId " +
            "AND a.eventType = 'file_upload' " +  // 조건 추가
            "ORDER BY fu.timestamp DESC LIMIT 10")
    List<MsRecentFileDTO> findRecentFilesByOrgIdAndSaasId(@Param("orgId") int orgId, @Param("saasId") int saasId);

    boolean existsBySaasFileIdAndTimestamp(String saasFileId, LocalDateTime timestamp);


    @Query("SELECT fu.orgSaaS.id FROM FileUploadTable fu WHERE fu.saasFileId = :fileId ORDER BY fu.timestamp DESC LIMIT 1")
    int findLatestOrgSaaSIdByFileId(@Param("fileId") String fileId);



    @Transactional
    @Modifying
    @Query("UPDATE FileUploadTable fu " +
            "SET fu.deleted = true " +
            "WHERE fu.saasFileId = :saasFileId AND fu.id IS NOT NULL")
    void checkDelete(@Param("saasFileId") String saasFileId);

}

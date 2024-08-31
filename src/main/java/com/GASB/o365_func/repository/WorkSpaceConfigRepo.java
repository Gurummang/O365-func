package com.GASB.o365_func.repository;

import com.GASB.o365_func.model.entity.WorkspaceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkSpaceConfigRepo extends JpaRepository<WorkspaceConfig, String> {

    @Query("SELECT config FROM WorkspaceConfig config WHERE config.id = :id")
    Optional<WorkspaceConfig> findById(@Param("id") int id);

    @Query("SELECT config.token FROM WorkspaceConfig config WHERE config.id = :id")
    String findByWorkSpaceId(@Param("id")int id);
    boolean existsById(int id);
    List<WorkspaceConfig> findByIdIn(List<Integer> configIds);
}

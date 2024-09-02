package com.GASB.o365_func.repository;


import com.GASB.o365_func.model.entity.AdminUsers;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminUsersRepo extends JpaRepository<AdminUsers, Integer> {

    @Query("SELECT a.org.id FROM AdminUsers a where a.email = :email")
    int findByEmail(@Param("email")String email);
    boolean existsByEmail(String email);
}

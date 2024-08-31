package com.GASB.o365_func.repository;


import com.GASB.o365_func.model.entity.AdminUsers;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminUsersRepo extends JpaRepository<AdminUsers, Integer> {
    Optional<AdminUsers> findByEmail(String email);
    boolean existsByEmail(String email);
}

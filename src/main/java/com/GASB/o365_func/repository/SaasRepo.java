package com.GASB.o365_func.repository;

import com.GASB.o365_func.model.entity.Saas;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SaasRepo extends JpaRepository<Saas, Integer> {


    Optional<Saas> findBySaasName(String saasName);
}

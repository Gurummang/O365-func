package com.GASB.o365_func.repository;


import com.GASB.o365_func.model.entity.TypeScan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TypeScanRepo extends JpaRepository<TypeScan, Integer> {
}

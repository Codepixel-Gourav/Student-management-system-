package com.example.SMS.repository;

import com.example.SMS.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

@Repository
public interface StudentRepository extends JpaRepository<Student, UUID> {
    Page<Student> findAllByTenantId(UUID tenantId, Pageable pageable);

    java.util.Optional<Student> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);
}

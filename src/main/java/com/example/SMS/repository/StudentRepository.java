package com.example.SMS.repository;

import com.example.SMS.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface StudentRepository extends JpaRepository<Student, UUID> {
    @Query("""
            SELECT student FROM Student student
            WHERE student.tenantId = :tenantId
              AND (:search = ''
                   OR LOWER(student.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(student.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(student.enrollmentNo) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(student.email, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(student.department, '')) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Student> searchByTenantId(
            @Param("tenantId") UUID tenantId,
            @Param("search") String search,
            Pageable pageable);

    java.util.Optional<Student> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndAdmissionStatus(UUID tenantId, String admissionStatus);

    @Query(value = """
            SELECT EXTRACT(YEAR FROM created_at)::int AS year,
                   EXTRACT(MONTH FROM created_at)::int AS month,
                   COUNT(*)::bigint AS count
            FROM students
            WHERE tenant_id = :tenantId
              AND created_at >= :start
              AND created_at < :end
            GROUP BY EXTRACT(YEAR FROM created_at), EXTRACT(MONTH FROM created_at)
            ORDER BY year, month
            """, nativeQuery = true)
    List<MonthlyEnrollmentProjection> countMonthlyEnrollments(
            @Param("tenantId") UUID tenantId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    interface MonthlyEnrollmentProjection {
        Integer getYear();
        Integer getMonth();
        Long getCount();
    }
}

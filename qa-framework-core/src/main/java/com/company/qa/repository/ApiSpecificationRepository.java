package com.company.qa.repository;

import com.company.qa.model.entity.ApiSpecification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApiSpecificationRepository extends JpaRepository<ApiSpecification, Long> {

    /**
     * Find by name
     */
    Optional<ApiSpecification> findByName(String name);

    /**
     * Find by name and version
     */
    Optional<ApiSpecification> findByNameAndVersion(String name, String version);

    /**
     * Find all active specifications
     */
    List<ApiSpecification> findByIsActiveTrueOrderByUploadedAtDesc();

    /**
     * Find by uploader
     */
    List<ApiSpecification> findByUploadedByOrderByUploadedAtDesc(String uploadedBy);

    /**
     * Find recent specifications
     */
    @Query("SELECT a FROM ApiSpecification a WHERE a.uploadedAt >= :since " +
            "ORDER BY a.uploadedAt DESC")
    List<ApiSpecification> findRecentSpecs(@Param("since") Instant since);

    /**
     * Find by OpenAPI version
     */
    List<ApiSpecification> findByOpenapiVersionOrderByUploadedAtDesc(String openapiVersion);

    /**
     * Count active specifications
     */
    long countByIsActiveTrue();

    /**
     * Search by name (case-insensitive)
     */
    @Query("SELECT a FROM ApiSpecification a WHERE LOWER(a.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "ORDER BY a.uploadedAt DESC")
    List<ApiSpecification> searchByName(@Param("searchTerm") String searchTerm);
}
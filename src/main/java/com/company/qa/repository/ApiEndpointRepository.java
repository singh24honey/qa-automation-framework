package com.company.qa.repository;

import com.company.qa.model.entity.ApiEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiEndpointRepository extends JpaRepository<ApiEndpoint, Long> {

    /**
     * Find all endpoints for a specification
     */
    List<ApiEndpoint> findBySpecificationIdOrderByPathAsc(Long specId);

    /**
     * Find by specification and method
     */
    List<ApiEndpoint> findBySpecificationIdAndMethodOrderByPathAsc(Long specId, String method);

    /**
     * Find by path (across all specs)
     */
    List<ApiEndpoint> findByPath(String path);

    /**
     * Find by specification, path, and method (unique)
     */
    Optional<ApiEndpoint> findBySpecificationIdAndPathAndMethod(Long specId, String path, String method);

    /**
     * Find endpoints by tag
     */
    @Query("SELECT e FROM ApiEndpoint e WHERE :tag = ANY(e.tags) " +
            "ORDER BY e.path ASC")
    List<ApiEndpoint> findByTag(@Param("tag") String tag);

    /**
     * Find deprecated endpoints
     */
    List<ApiEndpoint> findByIsDeprecatedTrue();

    /**
     * Find endpoints with request body
     */
    @Query("SELECT e FROM ApiEndpoint e WHERE e.requestSchema IS NOT NULL " +
            "AND e.specification.id = :specId ORDER BY e.path ASC")
    List<ApiEndpoint> findEndpointsWithRequestBody(@Param("specId") Long specId);

    /**
     * Count endpoints by specification
     */
    long countBySpecificationId(Long specId);

    /**
     * Search endpoints by path or summary
     */
    @Query("SELECT e FROM ApiEndpoint e WHERE " +
            "LOWER(e.path) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(e.summary) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "ORDER BY e.path ASC")
    List<ApiEndpoint> searchEndpoints(@Param("searchTerm") String searchTerm);
}
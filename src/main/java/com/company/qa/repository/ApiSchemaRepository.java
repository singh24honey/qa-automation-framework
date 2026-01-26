package com.company.qa.repository;

import com.company.qa.model.entity.ApiSchema;
import com.company.qa.model.entity.ApiSpecification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiSchemaRepository extends JpaRepository<ApiSchema, Long> {

    /**
     * Find all schemas for a specification
     */
    List<ApiSchema> findBySpecificationIdOrderBySchemaNameAsc(Long specId);

    /**
     * Find by specification and schema name (unique)
     */
    Optional<ApiSchema> findBySpecificationIdAndSchemaName(Long specId, String schemaName);

    /**
     * Find by schema type
     */
    List<ApiSchema> findBySchemaTypeOrderBySchemaNameAsc(String schemaType);

    /**
     * Find all enums
     */
    List<ApiSchema> findByIsEnumTrueOrderBySchemaNameAsc();

    /**
     * Find most used schemas
     */
    @Query("SELECT s FROM ApiSchema s WHERE s.specification.id = :specId " +
            "ORDER BY (s.usedInRequests + s.usedInResponses) DESC")
    List<ApiSchema> findMostUsedSchemas(@Param("specId") Long specId);

    /**
     * Count schemas by specification
     */
    long countBySpecificationId(Long specId);

    /**
     * Search schemas by name
     */
    @Query("SELECT s FROM ApiSchema s WHERE " +
            "LOWER(s.schemaName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "ORDER BY s.schemaName ASC")
    List<ApiSchema> searchSchemas(@Param("searchTerm") String searchTerm);

    Optional<ApiSchema> findBySpecificationAndSchemaName(ApiSpecification spec, String createUserRequest);
}
package com.company.qa.repository;

import com.company.qa.model.entity.Test;
import com.company.qa.model.enums.TestFramework;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TestRepository extends JpaRepository<Test, UUID> {

    List<Test> findByIsActiveTrue();

    List<Test> findByFrameworkAndIsActiveTrue(TestFramework framework);

    List<Test> findByNameContainingIgnoreCase(String name);
}
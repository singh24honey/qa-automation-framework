package com.company.qa.repository;

import com.company.qa.model.entity.AIGeneratedTest;
import com.company.qa.model.entity.Test;
import com.company.qa.model.enums.TestFramework;
import com.fasterxml.jackson.databind.introspect.AnnotationCollector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TestRepository extends JpaRepository<Test, UUID> {

    List<Test> findByIsActiveTrue();

    List<Test> findByFrameworkAndIsActiveTrue(TestFramework framework);

    List<Test> findByNameContainingIgnoreCase(String name);

    List<Test> findByNameAndFramework(String name, TestFramework testFramework);

    Test findFirstByDescriptionContainingIgnoreCase(String id);



}
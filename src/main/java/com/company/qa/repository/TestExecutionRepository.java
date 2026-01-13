package com.company.qa.repository;

import com.company.qa.model.entity.TestExecution;
import com.company.qa.model.enums.TestStatus;
import org.springdoc.core.converters.models.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface TestExecutionRepository extends JpaRepository<TestExecution, UUID> {

    List<TestExecution> findByTestId(UUID testId);

    List<TestExecution> findByStatus(TestStatus status);

    List<TestExecution> findByStartTimeBetween(Instant start, Instant end);

    List<TestExecution> findTop10ByOrderByStartTimeDesc();

}
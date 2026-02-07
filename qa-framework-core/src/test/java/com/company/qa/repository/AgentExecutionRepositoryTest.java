package com.company.qa.repository;

import com.company.qa.model.agent.entity.AgentExecution;
import com.company.qa.model.enums.AgentStatus;
import com.company.qa.model.enums.AgentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class AgentExecutionRepositoryTest {

    @Autowired
    private AgentExecutionRepository repository;

    @Test
    void testSaveAndFind() {
        AgentExecution execution = createTestExecution();

        AgentExecution saved = repository.save(execution);

        assertNotNull(saved.getId());
        assertEquals(AgentType.PLAYWRIGHT_TEST_GENERATOR, saved.getAgentType());
        assertEquals(AgentStatus.RUNNING, saved.getStatus());
    }

    @Test
    void testFindByAgentType() {
        repository.save(createTestExecution());

        List<AgentExecution> found = repository.findByAgentType(AgentType.PLAYWRIGHT_TEST_GENERATOR);

        assertTrue(found.size() > 0);
    }

    @Test
    void testFindByStatus() {
        repository.save(createTestExecution());

        List<AgentExecution> found = repository.findByStatus(AgentStatus.RUNNING);

        assertTrue(found.size() > 0);
    }

    @Test
    void testFindRunningAgents() {
        repository.save(createTestExecution());

        List<AgentExecution> running = repository.findRunningAgents();

        assertTrue(running.size() > 0);
    }

    private AgentExecution createTestExecution() {
        Map<String, Object> goal = new HashMap<>();
        goal.put("goalType", "GENERATE_TEST");
        goal.put("jiraKey", "PROJ-123");

        return AgentExecution.builder()
                .agentType(AgentType.PLAYWRIGHT_TEST_GENERATOR)
                .status(AgentStatus.RUNNING)
                .goal(goal)
                .currentIteration(0)
                .maxIterations(20)
                .startedAt(Instant.now())
                .build();
    }
}
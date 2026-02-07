package com.company.qa.service.agent.tool;

import com.company.qa.model.enums.AgentActionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentToolRegistryTest {

    private AgentToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AgentToolRegistry();
    }

    @Test
    void testRegisterTool() {
        AgentTool mockTool = createMockTool(AgentActionType.FETCH_JIRA_STORY, "Test Tool");

        registry.registerTool(mockTool);

        assertTrue(registry.hasToolFor(AgentActionType.FETCH_JIRA_STORY));
        assertEquals(1, registry.getToolCount());
    }

    @Test
    void testGetTool() {
        AgentTool mockTool = createMockTool(AgentActionType.FETCH_JIRA_STORY, "Test Tool");
        registry.registerTool(mockTool);

        var tool = registry.getTool(AgentActionType.FETCH_JIRA_STORY);

        assertTrue(tool.isPresent());
        assertEquals("Test Tool", tool.get().getName());
    }

    @Test
    void testGetToolNotFound() {
        var tool = registry.getTool(AgentActionType.FETCH_JIRA_STORY);

        assertTrue(tool.isEmpty());
    }

    @Test
    void testExecuteTool() {
        AgentTool mockTool = createMockTool(AgentActionType.FETCH_JIRA_STORY, "Test Tool");
        registry.registerTool(mockTool);

        Map<String, Object> params = Map.of("test", "value");
        Map<String, Object> result = registry.executeTool(AgentActionType.FETCH_JIRA_STORY, params);

        assertNotNull(result);
        assertEquals(true, result.get("success"));
    }

    @Test
    void testExecuteToolNotFound() {
        assertThrows(IllegalArgumentException.class, () -> {
            registry.executeTool(AgentActionType.FETCH_JIRA_STORY, Map.of());
        });
    }

    @Test
    void testExecuteToolInvalidParameters() {
        AgentTool mockTool = new AgentTool() {
            @Override
            public AgentActionType getActionType() {
                return AgentActionType.FETCH_JIRA_STORY;
            }

            @Override
            public String getName() {
                return "Test Tool";
            }

            @Override
            public String getDescription() {
                return "Test";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> parameters) {
                return Map.of();
            }

            @Override
            public boolean validateParameters(Map<String, Object> parameters) {
                return false; // Always invalid
            }

            @Override
            public Map<String, String> getParameterSchema() {
                return Map.of();
            }
        };

        registry.registerTool(mockTool);

        assertThrows(IllegalArgumentException.class, () -> {
            registry.executeTool(AgentActionType.FETCH_JIRA_STORY, Map.of());
        });
    }

    @Test
    void testGetToolCatalog() {
        AgentTool tool1 = createMockTool(AgentActionType.FETCH_JIRA_STORY, "JIRA Fetcher");
        AgentTool tool2 = createMockTool(AgentActionType.GENERATE_TEST_CODE, "Test Generator");

        registry.registerTool(tool1);
        registry.registerTool(tool2);

        String catalog = registry.getToolCatalog();

        assertTrue(catalog.contains("JIRA Fetcher"));
        assertTrue(catalog.contains("Test Generator"));
        assertTrue(catalog.contains("FETCH_JIRA_STORY"));
        assertTrue(catalog.contains("GENERATE_TEST_CODE"));
    }

    @Test
    void testGetAllTools() {
        AgentTool tool1 = createMockTool(AgentActionType.FETCH_JIRA_STORY, "Tool 1");
        AgentTool tool2 = createMockTool(AgentActionType.GENERATE_TEST_CODE, "Tool 2");

        registry.registerTool(tool1);
        registry.registerTool(tool2);

        List<AgentTool> tools = registry.getAllTools();

        assertEquals(2, tools.size());
    }

    @Test
    void testGetToolsByCategory() {
        AgentTool jiraTool = createMockTool(AgentActionType.FETCH_JIRA_STORY, "JIRA Tool");
        AgentTool gitTool = createMockTool(AgentActionType.COMMIT_CHANGES, "Git Tool");

        registry.registerTool(jiraTool);
        registry.registerTool(gitTool);

        Map<String, List<AgentTool>> byCategory = registry.getToolsByCategory();

        assertTrue(byCategory.size() > 0);
    }

    private AgentTool createMockTool(AgentActionType actionType, String name) {
        return new AgentTool() {
            @Override
            public AgentActionType getActionType() {
                return actionType;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDescription() {
                return "Mock tool for testing";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> parameters) {
                return Map.of("success", true);
            }

            @Override
            public boolean validateParameters(Map<String, Object> parameters) {
                return true;
            }

            @Override
            public Map<String, String> getParameterSchema() {
                return new HashMap<>();
            }
        };
    }
}
package com.company.qa.service.agent;

import com.company.qa.model.intent.IntentActionType;
import com.company.qa.model.intent.IntentTestStep;
import com.company.qa.model.intent.TestIntent;
import com.company.qa.model.intent.TestScenario;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Detects the format of Test.content and extracts a unified list of steps
 * regardless of whether the test was created in legacy or Zero-Hallucination mode.
 *
 * Three formats in the wild:
 *
 *   LEGACY      — {"steps": [{"action":"click","locator":"css=#btn"},...]}
 *   INTENT_JSON — {"testClassName":"X","scenarios":[{"steps":[...]}]}
 *   RENDERED_JAVA — "package com.company...\npublic class X extends Base..."
 *
 * Agents call getSteps() to get a flat list of IntentTestStep without caring
 * about which format the content is in.
 *
 * @author QA Framework
 * @since Phase 1 — Intent Compatibility
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestContentResolver {

    private final ObjectMapper objectMapper;

    public enum ContentFormat { LEGACY_STEPS, INTENT_JSON, RENDERED_JAVA, UNKNOWN }

    /**
     * Detect which format Test.content is in.
     */
    public ContentFormat detect(String content) {
        if (content == null || content.isBlank()) return ContentFormat.UNKNOWN;

        String trimmed = content.trim();

        // Rendered Java starts with a package declaration or class keyword
        if (trimmed.startsWith("package ") || trimmed.startsWith("public class ")
                || trimmed.startsWith("import ")) {
            return ContentFormat.RENDERED_JAVA;
        }

        if (!trimmed.startsWith("{")) return ContentFormat.UNKNOWN;

        try {
            Map<?, ?> map = objectMapper.readValue(trimmed, Map.class);
            if (map.containsKey("scenarios")) return ContentFormat.INTENT_JSON;
            if (map.containsKey("steps"))    return ContentFormat.LEGACY_STEPS;
        } catch (Exception ignored) { /* not JSON */ }

        return ContentFormat.UNKNOWN;
    }

    /**
     * Extract a flat list of steps from ANY content format.
     *
     * LEGACY      → maps each step map directly to IntentTestStep
     * INTENT_JSON → flattens steps from all scenarios
     * RENDERED_JAVA → returns empty list (cannot recover steps from compiled Java)
     */
    @SuppressWarnings("unchecked")
    public List<IntentTestStep> getSteps(String content) {
        ContentFormat format = detect(content);
        log.debug("TestContentResolver: detected format={}", format);

        return switch (format) {
            case LEGACY_STEPS    -> extractLegacySteps(content);
            case INTENT_JSON     -> extractIntentSteps(content);
            case RENDERED_JAVA   -> {
                log.warn("Content is rendered Java — cannot extract steps. " +
                        "SelfHealingAgent will fall back to AI discovery.");
                yield List.of();
            }
            default -> {
                log.warn("Unknown content format — returning empty step list.");
                yield List.of();
            }
        };
    }

    /**
     * Replace a specific locator in content, preserving the original format.
     *
     * LEGACY_STEPS  → find step by brokenLocator, replace locator value, re-serialize
     * INTENT_JSON   → find step across all scenarios, replace, re-serialize
     * RENDERED_JAVA → not supported (returns original unchanged)
     */
    @SuppressWarnings("unchecked")
    public String replaceLocator(String content, String brokenLocator, String newLocator) {
        ContentFormat format = detect(content);

        try {
            if (format == ContentFormat.LEGACY_STEPS) {
                Map<String, Object> map = objectMapper.readValue(content, Map.class);
                List<Map<String, Object>> steps =
                        (List<Map<String, Object>>) map.get("steps");
                replaceInStepList(steps, brokenLocator, newLocator);
                return objectMapper.writeValueAsString(map);
            }

            if (format == ContentFormat.INTENT_JSON) {
                Map<String, Object> map = objectMapper.readValue(content, Map.class);
                List<Map<String, Object>> scenarios =
                        (List<Map<String, Object>>) map.get("scenarios");
                for (Map<String, Object> scenario : scenarios) {
                    List<Map<String, Object>> steps =
                            (List<Map<String, Object>>) scenario.get("steps");
                    replaceInStepList(steps, brokenLocator, newLocator);
                }
                return objectMapper.writeValueAsString(map);
            }

            log.warn("replaceLocator: format {} not supported — returning unchanged", format);
            return content;

        } catch (Exception e) {
            log.error("replaceLocator failed: {}", e.getMessage());
            return content;
        }
    }

    // ── private helpers ─────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<IntentTestStep> extractLegacySteps(String content) {
        try {
            Map<String, Object> map = objectMapper.readValue(content, Map.class);
            List<Map<String, Object>> rawSteps =
                    (List<Map<String, Object>>) map.get("steps");
            return mapRawSteps(rawSteps);
        } catch (Exception e) {
            log.error("Failed to extract legacy steps: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<IntentTestStep> extractIntentSteps(String content) {
        try {
            TestIntent intent = objectMapper.readValue(content, TestIntent.class);
            List<IntentTestStep> all = new ArrayList<>();
            for (TestScenario scenario : intent.getScenarios()) {
                all.addAll(scenario.getSteps());
            }
            return all;
        } catch (Exception e) {
            log.error("Failed to extract intent steps: {}", e.getMessage());
            return List.of();
        }
    }

    private List<IntentTestStep> mapRawSteps(List<Map<String, Object>> rawSteps) {
        if (rawSteps == null) return List.of();
        List<IntentTestStep> result = new ArrayList<>();
        for (Map<String, Object> raw : rawSteps) {
            String actionStr = (String) raw.get("action");
            IntentActionType action = null;
            try {
                action = IntentActionType.fromString(actionStr);
            } catch (Exception ignored) { /* unknown action */ }

            result.add(IntentTestStep.builder()
                    .action(action)
                    .locator((String) raw.get("locator"))
                    .value((String) raw.get("value"))
                    .description((String) raw.get("description"))
                    .build());
        }
        return result;
    }

    private void replaceInStepList(List<Map<String, Object>> steps,
                                   String brokenLocator, String newLocator) {
        if (steps == null) return;
        for (Map<String, Object> step : steps) {
            if (brokenLocator.equals(step.get("locator"))) {
                step.put("locator", newLocator);
                log.info("  Replaced locator: {} → {}", brokenLocator, newLocator);
            }
        }
    }
}
package com.company.qa.service.agent.tool.impl;

import com.company.qa.model.dto.SecureAIRequest;
import com.company.qa.model.dto.SecureAIResponse;
import com.company.qa.model.enums.AgentActionType;
import com.company.qa.model.enums.UserRole;
import com.company.qa.service.agent.tool.AgentTool;
import com.company.qa.service.agent.tool.AgentToolRegistry;
import com.company.qa.service.ai.AIGatewayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Tool to discover new locators using AI analysis of page HTML.
 *
 * When registry has no alternatives, this tool:
 * 1. Takes page HTML
 * 2. Asks AI to find the element
 * 3. Returns suggested locators in priority order
 *
 * Input parameters:
 * - pageHtml: HTML content of the page
 * - brokenLocator: The locator that failed
 * - elementPurpose: What the element does
 * - pageName: Name of the page
 *
 * Output:
 * - success: true/false
 * - suggestions: List of suggested locators
 * - primarySuggestion: Best suggestion
 * - aiReasoning: Why AI chose these locators
 * - error: Error message if failed
 *
 * @author QA Framework
 * @since Week 16 Day 4
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscoverLocatorTool implements AgentTool {

    private final AIGatewayService aiGateway;
    private final ObjectMapper objectMapper;
    private final AgentToolRegistry toolRegistry;

    @PostConstruct
    public void register() {
        toolRegistry.registerTool(this);
    }

    @Override
    public AgentActionType getActionType() {
        return AgentActionType.DISCOVER_LOCATOR; // Analyzing HTML to find locator
    }

    @Override
    public String getName() {
        return "AI Locator Discovery Tool";
    }

    @Override
    public String getDescription() {
        return "Discovers new locators using AI analysis of page HTML. " +
                "Fallback when Element Registry has no alternatives. " +
                "Returns suggested locators with confidence scores.";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> parameters) {
        log.info("🤖 Using AI to discover new locator");

        try {
            String pageHtml = (String) parameters.get("pageHtml");
            String brokenLocator = (String) parameters.get("brokenLocator");
            String elementPurpose = (String) parameters.get("elementPurpose");
            String pageName = (String) parameters.get("pageName");
            String actionType = parameters.get("actionType") == null ? "unknown" : (String) parameters.get("actionType");
            String stepLocator = parameters.get("stepLocator") == null ? "unknown" : (String) parameters.get("stepLocator");

            // Build discovery prompt
            String prompt = buildDiscoveryPrompt(pageHtml, brokenLocator, elementPurpose, pageName, actionType, stepLocator);

            log.info("Asking AI to discover locator for: {} on page: {}", elementPurpose, pageName);

            // Call AI
            SecureAIRequest aiRequest = SecureAIRequest.builder()
                    .content(prompt)
                    .operationType(SecureAIRequest.OperationType.LOCATOR_DISCOVERY)
                    .userId(UUID.randomUUID())
                    .userRole(UserRole.QA_ENGINEER)
                    .testName(pageName + " - " + elementPurpose)
                    .build();

            SecureAIResponse aiResponse = aiGateway.analyzeFailure(aiRequest);

            if (!aiResponse.isSuccess()) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("error", "AI discovery failed: " + aiResponse.getErrorMessage());
                return result;
            }

            // FIX 1: parseDiscoveryResponse never throws — handles markdown/prose gracefully.
            // Root cause: when brokenLocator="UNKNOWN", AI ignores JSON format and returns
            // "### Root Cause Analysis..." — objectMapper.readValue() crashed on '#' char.
            Map<String, Object> discoveryResult = parseDiscoveryResponse(aiResponse.getContent());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> suggestions = discoveryResult.containsKey("suggestions")
                    ? (List<Map<String, Object>>) discoveryResult.get("suggestions")
                    : (List<Map<String, Object>>) discoveryResult.get("recommended_fixes");

            // FIX 2: Never return success=false for empty/null suggestions.
            // An empty list signals the planner to escalate to manual review.
            // success=false leaves aiSuggestions=null → planner loops forever on planAiDiscovery().
            if (suggestions == null) suggestions = List.of();

            if (suggestions.isEmpty()) {
                log.warn("⚠️ AI returned no usable locator suggestions for brokenLocator='{}'", brokenLocator);
            } else {
                log.info("✅ AI discovered {} locator suggestions", suggestions.size());
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("suggestions", suggestions);
            result.put("primarySuggestion", suggestions.isEmpty() ? null : suggestions.get(0));
            result.put("aiReasoning", discoveryResult.get("reasoning"));
            result.put("totalSuggestions", suggestions.size());

            return result;

        } catch (Exception e) {
            log.error("❌ AI discovery failed unexpectedly: {}", e.getMessage(), e);

            // Return success=true with empty list — consistent with the empty-suggestions path above.
            // Prevents the catch block from returning success=false and causing aiSuggestions=null loop.
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("suggestions", List.of());
            result.put("totalSuggestions", 0);
            result.put("error", e.getMessage());
            return result;
        }
    }

    @Override
    public boolean validateParameters(Map<String, Object> parameters) {
        return parameters != null &&
                parameters.containsKey("pageHtml") &&
                parameters.containsKey("brokenLocator") &&
                parameters.containsKey("elementPurpose");
    }

    @Override
    public Map<String, String> getParameterSchema() {
        Map<String, String> schema = new HashMap<>();
        schema.put("pageHtml", "string (required) - Page HTML content");
        schema.put("brokenLocator", "string (required) - Broken locator");
        schema.put("elementPurpose", "string (required) - Element purpose");
        schema.put("pageName", "string (optional) - Page name");
        return schema;
    }

    /**
     * Build AI prompt for locator discovery.
     */
    private String buildDiscoveryPrompt(String html, String brokenLocator,
                                        String purpose, String pageName,
                                        String actionType, String originalLocator) {

        String locatorContext = originalLocator != null
                ? String.format("ORIGINAL LOCATOR: %s\nBROKEN LOCATOR: %s", originalLocator, brokenLocator)
                : String.format("BROKEN LOCATOR: %s", brokenLocator);

        String actionContext = actionType != null
                ? String.format("ACTION TYPE: %s (this element is being %s)",
                actionType,
                actionType.equals("CLICK") ? "clicked" :
                        actionType.equals("ASSERT_TEXT") ? "checked for text content" : "interacted with")
                : "";
        return """
        You are analyzing a broken test locator and need to suggest alternatives.
        
        BROKEN LOCATOR: %s
        ELEMENT PURPOSE: %s
        PAGE: %s
        
        PAGE HTML:
        %s
        
        ⚠️  CRITICAL OUTPUT FORMAT REQUIREMENTS:
        
        Return ONLY CSS or XPath selector strings, NOT Java code.
        
        ❌ WRONG (Java code):
        page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Username"))
        page.getByPlaceholder("Username")
        
        ✅ CORRECT (selector strings):
        [data-test="username"]
        #username
        input[name="user-name"]
        input[placeholder="Username"]
        //input[@data-test='username']
        
        Return JSON with EXACTLY this structure:
        {
          "suggestions": [
            {
              "locator": "[data-test='username']",
              "strategy": "attribute",
              "confidence": 0.95,
              "reasoning": "data-test attribute is stable"
            },
            {
              "locator": "#user-name",
              "strategy": "id",
              "confidence": 0.90,
              "reasoning": "ID selector as fallback"
            },
            {
              "locator": "input[placeholder='Username']",
              "strategy": "attribute",
              "confidence": 0.85,
              "reasoning": "Placeholder text selector"
            }
          ]
        }
        
        REQUIREMENTS:
        1. Return EXACTLY 3 suggestions
        2. Each "locator" MUST be a CSS or XPath selector STRING
        3. NO Java code (no page., no getBy, no AriaRole)
        4. Order by stability (most stable first)
        5. Valid strategies: "attribute", "id", "class", "xpath", "text"
        
        VALID SELECTOR FORMATS:
        - CSS attribute: [data-test='value'], [name='value'], [placeholder='value']
        - CSS ID: #element-id
        - CSS class: .class-name
        - CSS tag+attribute: input[type='text']
        - XPath: //tag[@attribute='value']
        
        DO NOT USE:
        - Java method calls
        - Playwright API calls
        - getByRole, getByLabel, getByPlaceholder
        - Any code with parentheses or method calls
        """.formatted(locatorContext, purpose, actionContext, pageName, html);
    }

    /**
     * Parse AI discovery response — NEVER throws.
     *
     * Strategy order:
     * 1. Strip markdown fences, parse directly  (happy path: AI returned valid JSON)
     * 2. Extract first {...} block from prose   (AI added preamble before/after JSON)
     * 3. Return empty map                       (AI returned pure prose — no JSON at all)
     *
     * Root cause this fixes: when brokenLocator="UNKNOWN" / elementPurpose="unknown",
     * the AI has no meaningful context and ignores the JSON format instruction,
     * returning a Markdown essay starting with "### Root Cause Analysis...".
     * The old implementation called objectMapper.readValue() directly which threw
     * JsonParseException on the '#' character, crashing the whole tool invocation.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseDiscoveryResponse(String response) {
        if (response == null || response.isBlank()) {
            log.warn("⚠️ AI returned empty response for locator discovery");
            return Map.of();
        }

        // Strategy 1: strip markdown fences and try direct parse
        String cleaned = stripMarkdownFences(response);
        try {
            Map<String, Object> parsed = objectMapper.readValue(cleaned, Map.class);
            if (parsed.containsKey("suggestions") || parsed.containsKey("recommended_fixes")) {
                return parsed;
            }
        } catch (Exception ignored) {}

        // Strategy 2: find first {...} JSON block embedded in prose
        int firstBrace = response.indexOf('{');
        int lastBrace  = response.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            String extracted = response.substring(firstBrace, lastBrace + 1);
            try {
                Map<String, Object> parsed = objectMapper.readValue(extracted, Map.class);
                if (parsed.containsKey("suggestions") || parsed.containsKey("recommended_fixes")) {
                    log.info("✅ Extracted JSON block from prose AI response");
                    return parsed;
                }
            } catch (Exception ignored) {}
        }

        // Strategy 3: pure prose — no JSON extractable
        log.warn("⚠️ AI response is prose (not JSON) — no locators extractable. Agent will escalate to manual review.");
        return Map.of();
    }

    private String stripMarkdownFences(String text) {
        String s = text.trim();
        if (s.startsWith("```json")) s = s.substring(7);
        else if (s.startsWith("```"))  s = s.substring(3);
        if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        return s.trim();
    }
}
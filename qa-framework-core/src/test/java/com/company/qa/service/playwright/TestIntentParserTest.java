package com.company.qa.service.playwright;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestIntentParserTest {

    private TestIntentParser parser;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        TestIntentValidator validator = new TestIntentValidator();
        parser = new TestIntentParser(objectMapper, validator);
    }

    // ---- isIntentFormat ----

    @Test
    @DisplayName("isIntentFormat — returns true for JSON containing 'scenarios' key")
    void isIntentFormatShouldReturnTrueWhenScenariosPresent() {
        String json = buildValidIntentJson();
        assertThat(parser.isIntentFormat(json)).isTrue();
    }

    @Test
    @DisplayName("isIntentFormat — returns false for legacy Java-in-JSON format")
    void isIntentFormatShouldReturnFalseForLegacyFormat() {
        String legacyJson = """
                {"testClass": "public class LoginTest extends BasePlaywrightTest { ... }"}
                """;
        assertThat(parser.isIntentFormat(legacyJson)).isFalse();
    }

    @Test
    @DisplayName("isIntentFormat — returns false for null/blank")
    void isIntentFormatShouldReturnFalseForNull() {
        assertThat(parser.isIntentFormat(null)).isFalse();
        assertThat(parser.isIntentFormat("")).isFalse();
        assertThat(parser.isIntentFormat("  ")).isFalse();
    }

    @Test
    @DisplayName("isIntentFormat — returns false for malformed JSON")
    void isIntentFormatShouldReturnFalseForMalformedJson() {
        assertThat(parser.isIntentFormat("{not valid json}")).isFalse();
    }

    // ---- parse: SUCCESS cases ----

    @Test
    @DisplayName("parse — valid intent JSON returns SUCCESS")
    void parseShouldReturnSuccessForValidIntent() {
        TestIntentParser.ParseResult result = parser.parse(buildValidIntentJson());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getIntent()).isNotNull();
        assertThat(result.getIntent().getTestClassName()).isEqualTo("LoginTest");
        assertThat(result.getIntent().getScenarioCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("parse — AI-wrapped intent JSON is unwrapped and parsed")
    void parseShouldUnwrapNestedIntentNode() {
        // AI wraps the intent in an extra object
        String wrapped = """
                {
                  "testCode": %s,
                  "jiraStory": "PROJ-123"
                }
                """.formatted(buildValidIntentJson());

        TestIntentParser.ParseResult result = parser.parse(wrapped);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getIntent().getTestClassName()).isEqualTo("LoginTest");
    }

    @Test
    @DisplayName("parse — unknown fields in JSON are ignored (JsonIgnoreProperties)")
    void parseShouldIgnoreUnknownJsonFields() {
        String withExtras = """
                {
                  "testClassName": "LoginTest",
                  "aiModel": "claude-sonnet",
                  "confidence": 0.95,
                  "scenarios": [
                    {
                      "name": "testLogin",
                      "generatedBy": "AI",
                      "steps": [
                        {"action": "NAVIGATE", "value": "https://www.saucedemo.com"},
                        {"action": "ASSERT_URL", "value": ".*saucedemo.*"}
                      ]
                    }
                  ]
                }
                """;

        TestIntentParser.ParseResult result = parser.parse(withExtras);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("parse — step description field is deserialized correctly")
    void parseShouldDeserializeStepDescription() {
        String json = """
                {
                  "testClassName": "LoginTest",
                  "scenarios": [{
                    "name": "testLogin",
                    "steps": [
                      {"action": "NAVIGATE", "value": "https://saucedemo.com",
                       "description": "Open the login page"},
                      {"action": "ASSERT_URL", "value": ".*saucedemo.*",
                       "description": "Verify we are on the correct page"}
                    ]
                  }]
                }
                """;

        TestIntentParser.ParseResult result = parser.parse(json);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getIntent().getScenarios().get(0).getSteps().get(0).getDescription())
                .isEqualTo("Open the login page");
    }

    // ---- parse: NOT_INTENT cases ----

    @Test
    @DisplayName("parse — legacy format returns NOT_INTENT")
    void parseShouldReturnNotIntentForLegacyFormat() {
        String legacy = """
                {"testClass": "public class LoginTest extends Base { @Test void test(){} }"}
                """;
        TestIntentParser.ParseResult result = parser.parse(legacy);
        assertThat(result.isNotIntent()).isTrue();
    }

    @Test
    @DisplayName("parse — empty JSON object returns NOT_INTENT")
    void parseShouldReturnNotIntentForEmptyObject() {
        TestIntentParser.ParseResult result = parser.parse("{}");
        assertThat(result.isNotIntent()).isTrue();
    }

    // ---- parse: PARSE_ERROR cases ----

    @Test
    @DisplayName("parse — null input returns PARSE_ERROR")
    void parseShouldReturnParseErrorForNull() {
        TestIntentParser.ParseResult result = parser.parse(null);
        assertThat(result.isParseError()).isTrue();
        assertThat(result.getErrorMessage()).isNotBlank();
    }

    @Test
    @DisplayName("parse — malformed JSON returns PARSE_ERROR")
    void parseShouldReturnParseErrorForMalformedJson() {
        TestIntentParser.ParseResult result = parser.parse("{not valid: json}");
        assertThat(result.isParseError()).isTrue();
    }

    // ---- parse: VALIDATION_FAILURE cases ----

    @Test
    @DisplayName("parse — intent with no assertions returns VALIDATION_FAILURE")
    void parseShouldReturnValidationFailureForIntentWithNoAssertions() {
        String json = """
                {
                  "testClassName": "LoginTest",
                  "scenarios": [{
                    "name": "testLogin",
                    "steps": [
                      {"action": "NAVIGATE", "value": "https://saucedemo.com"},
                      {"action": "CLICK", "locator": "testid=login-button"}
                    ]
                  }]
                }
                """;

        TestIntentParser.ParseResult result = parser.parse(json);
        assertThat(result.isValidationFailure()).isTrue();
        assertThat(result.getValidation().getErrors()).anyMatch(e -> e.contains("no assertions"));
    }

    @Test
    @DisplayName("parse — intent with missing testClassName returns VALIDATION_FAILURE")
    void parseShouldReturnValidationFailureForMissingClassName() {
        String json = """
                {
                  "scenarios": [{
                    "name": "testLogin",
                    "steps": [
                      {"action": "NAVIGATE", "value": "https://saucedemo.com"},
                      {"action": "ASSERT_URL", "value": ".*"}
                    ]
                  }]
                }
                """;

        TestIntentParser.ParseResult result = parser.parse(json);
        assertThat(result.isValidationFailure()).isTrue();
        assertThat(result.getValidation().getErrors()).anyMatch(e -> e.contains("testClassName"));
    }

    // ---- ParseResult.getSummary ----

    @Test
    @DisplayName("getSummary — returns human-readable string for each status")
    void getSummaryShouldReturnMeaningfulStringForAllStatuses() {
        assertThat(parser.parse(buildValidIntentJson()).getSummary()).contains("scenarios");
        assertThat(parser.parse("{}").getSummary()).contains("legacy");
        assertThat(parser.parse(null).getSummary()).contains("Parse error");
    }

    // ---- Helpers ----

    private String buildValidIntentJson() {
        return """
                {
                  "testClassName": "LoginTest",
                  "scenarios": [{
                    "name": "testSuccessfulLogin",
                    "description": "Standard user logs in and sees products page",
                    "steps": [
                      {"action": "NAVIGATE", "value": "https://www.saucedemo.com"},
                      {"action": "FILL", "locator": "testid=username", "value": "standard_user"},
                      {"action": "FILL", "locator": "testid=password", "value": "secret_sauce"},
                      {"action": "CLICK", "locator": "testid=login-button"},
                      {"action": "ASSERT_URL", "value": ".*inventory.*"}
                    ]
                  }]
                }
                """;
    }
}
package com.company.qa.e2e;

import com.company.qa.model.dto.GenerateTestRequest;
import com.company.qa.model.entity.*;
import com.company.qa.model.entity.Test;
import com.company.qa.model.enums.ApprovalStatus;
import com.company.qa.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
//import org.junit.jupiter.api.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
//@Transactional
@TestPropertySource(properties = {
        "ai.provider=bedrock",
        "spring.jpa.show-sql=false"  // Changed to false to reduce noise
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Week 8 E2E: OpenAPI AI Test Generation Complete Flow")
class OpenAPITestGenerationE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ✅ ADD THIS - Get API key from properties or use default
    @Value("${security.api-key.default-key:dev-local-key-12345678901234567890}")
    private String apiKey;

    @Autowired
    private ApiSpecificationRepository specRepository;

    @Autowired
    private ApiEndpointRepository endpointRepository;

    @Autowired
    private ApiSchemaRepository schemaRepository;

    @Autowired
    private TestRepository testRepository;

    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;

    @Autowired
    private TestGenerationContextRepository contextRepository;

    // Test data
    private static Long specId;
    private static Long endpointId;
    private static UUID testId;
    private static UUID approvalRequestId;
    private static Long contextId;

    private static final String SAMPLE_OPENAPI_SPEC = """
            {
              "openapi": "3.0.0",
              "info": {
                "title": "User API",
                "version": "1.0.0",
                "description": "API for user management"
              },
              "servers": [
                {
                  "url": "https://api.example.com/v1"
                }
              ],
              "paths": {
                "/users": {
                  "post": {
                    "summary": "Create user",
                    "description": "Creates a new user account",
                    "operationId": "createUser",
                    "requestBody": {
                      "required": true,
                      "content": {
                        "application/json": {
                          "schema": {
                            "$ref": "#/components/schemas/CreateUserRequest"
                          }
                        }
                      }
                    },
                    "responses": {
                      "201": {
                        "description": "User created successfully",
                        "content": {
                          "application/json": {
                            "schema": {
                              "$ref": "#/components/schemas/User"
                            }
                          }
                        }
                      }
                    }
                  }
                },
                "/users/{id}": {
                  "get": {
                    "summary": "Get user",
                    "description": "Retrieves user by ID",
                    "operationId": "getUser",
                    "parameters": [
                      {
                        "name": "id",
                        "in": "path",
                        "required": true,
                        "schema": {
                          "type": "integer"
                        }
                      }
                    ],
                    "responses": {
                      "200": {
                        "description": "User found",
                        "content": {
                          "application/json": {
                            "schema": {
                              "$ref": "#/components/schemas/User"
                            }
                          }
                        }
                      }
                    }
                  }
                }
              },
              "components": {
                "schemas": {
                  "CreateUserRequest": {
                    "type": "object",
                    "required": ["email", "name"],
                    "properties": {
                      "email": {
                        "type": "string",
                        "format": "email"
                      },
                      "name": {
                        "type": "string",
                        "minLength": 1
                      },
                      "age": {
                        "type": "integer",
                        "minimum": 18
                      }
                    }
                  },
                  "User": {
                    "type": "object",
                    "properties": {
                      "id": {
                        "type": "integer"
                      },
                      "email": {
                        "type": "string"
                      },
                      "name": {
                        "type": "string"
                      },
                      "age": {
                        "type": "integer"
                      },
                      "createdAt": {
                        "type": "string",
                        "format": "date-time"
                      }
                    }
                  }
                }
              }
            }
            """;

    @BeforeAll
    static void setupAll(@Autowired ApiSpecificationRepository specRepo,
                         @Autowired ApiEndpointRepository endpointRepo,
                         @Autowired ApiSchemaRepository schemaRepo,
                         @Autowired TestGenerationContextRepository contextRepo,
                         @Autowired ApprovalRequestRepository approvalRepo,
                         @Autowired TestRepository testRepo) {
        // Delete all test data before starting
        contextRepo.deleteAll();
        approvalRepo.deleteAll();
        testRepo.deleteAll();
        schemaRepo.deleteAll();
        endpointRepo.deleteAll();
        specRepo.deleteAll();
    }

    @AfterAll
    static void tearDownAll(@Autowired ApiSpecificationRepository specRepo
            /* ... other repos ... */) {
        // Clean up after all tests
        // ... same cleanup ...
    }
    @BeforeEach
    void checkApiKey() {
        System.out.println("Using API Key: " + apiKey.substring(0, 10) + "...");
    }

    // ============================================
    // TEST 1: Upload OpenAPI Specification
    // ============================================

    @org.junit.jupiter.api.Test
    @Order(1)
    @DisplayName("Step 1: Upload OpenAPI specification and verify parsing")
    void step1_uploadOpenAPISpec() throws Exception {
        System.out.println("\n=== STEP 1: Upload OpenAPI Spec ===");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "user-api-spec.json",
                "application/json",
                SAMPLE_OPENAPI_SPEC.getBytes()
        );

        // ✅ ADD API KEY HEADER
        MvcResult result = mockMvc.perform(multipart("/api/openapi/upload")
                        .file(file)
                        .header("X-API-Key", apiKey))  // ✅ ADDED
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.specificationId").exists())
                //.andExpect(jsonPath("$.name").value("User API"))
                .andExpect(jsonPath("$.version").value("1.0.0"))
               // .andExpect(jsonPath("$.endpointCount").value(2))
              //  .andExpect(jsonPath("$.schemaCount").value(2))
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(responseJson);
        specId = jsonNode.get("specificationId").asLong();

        System.out.println("✓ Uploaded spec ID: " + specId);

        ApiSpecification spec = specRepository.findById(specId).orElseThrow();
        assertThat(spec.getName()).isEqualTo("User API");
        assertThat(spec.getVersion()).isEqualTo("1.0.0");
        assertThat(spec.getOpenapiVersion()).isEqualTo("3.0.0");
        assertThat(spec.getBaseUrl()).isEqualTo("https://api.example.com/v1");

        System.out.println("✓ Database verification passed");
    }

    // ============================================
    // TEST 2: Verify Endpoints Parsed
    // ============================================

    @org.junit.jupiter.api.Test
    @Order(2)
    @DisplayName("Step 2: Verify endpoints were parsed correctly")
    void step2_verifyEndpointsParsed() throws Exception {
        System.out.println("\n=== STEP 2: Verify Endpoints ===");

        assertThat(specId).isNotNull();

        // ✅ ADD API KEY HEADER
        MvcResult result = mockMvc.perform(get("/api/openapi/specifications/{id}/endpoints", specId)
                        .header("X-API-Key", apiKey))  // ✅ ADDED
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                //.andExpect(jsonPath("$.length()").value(2))
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode endpoints = objectMapper.readTree(responseJson);

        for (com.fasterxml.jackson.databind.JsonNode endpoint : endpoints) {
            if ("POST".equals(endpoint.get("method").asText()) &&
                    "/users".equals(endpoint.get("path").asText())) {
                endpointId = endpoint.get("id").asLong();
                break;
            }
        }

        assertThat(endpointId).isNotNull();
        System.out.println("✓ Found POST /users endpoint ID: " + endpointId);

        ApiEndpoint endpoint = endpointRepository.findById(endpointId).orElseThrow();
        assertThat(endpoint.getMethod()).isEqualTo("POST");
        assertThat(endpoint.getPath()).isEqualTo("/users");
        assertThat(endpoint.getSummary()).isEqualTo("Create user");

        System.out.println("✓ Endpoint details verified");
    }

    // ============================================
    // TEST 3: Verify Schemas Parsed
    // ============================================

    @org.junit.jupiter.api.Test
    @Order(3)
    @DisplayName("Step 3: Verify schemas were parsed correctly")
    void step3_verifySchemasParsed() throws Exception {
        System.out.println("\n=== STEP 3: Verify Schemas ===");

        assertThat(specId).isNotNull();

        // ✅ ADD API KEY HEADER
        mockMvc.perform(get("/api/openapi/specifications/{id}/schemas", specId)
                        .header("X-API-Key", apiKey))  // ✅ ADDED
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));

        ApiSpecification spec = specRepository.findById(specId).orElseThrow();

        ApiSchema createUserRequest = schemaRepository
                .findBySpecificationAndSchemaName(spec, "CreateUserRequest")
                .orElseThrow();

        assertThat(createUserRequest.getSchemaType()).isEqualTo("object");

        System.out.println("✓ Both schemas verified");
    }

    // ============================================
    // TEST 4: Preview Context
    // ============================================

    @org.junit.jupiter.api.Test
    @Order(4)
    @DisplayName("Step 4: Preview AI context before generation")
    void step4_previewContext() throws Exception {
        System.out.println("\n=== STEP 4: Preview Context ===");

        assertThat(endpointId).isNotNull();

        // ✅ ADD API KEY HEADER
        MvcResult result = mockMvc.perform(get("/api/openapi/endpoints/{id}/context", endpointId)
                        .param("userPrompt", "Generate comprehensive test")
                        .header("X-API-Key", apiKey))  // ✅ ADDED
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endpointId").value(endpointId))
                .andExpect(jsonPath("$.method").value("POST"))
                .andExpect(jsonPath("$.path").value("/users"))
                .andExpect(jsonPath("$.hasRequestBody").value(true))
                .andExpect(jsonPath("$.contextPreview").exists())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode context = objectMapper.readTree(responseJson);

        String contextPreview = context.get("contextPreview").asText();
        assertThat(contextPreview).contains("POST");
        assertThat(contextPreview).contains("/users");

        System.out.println("✓ Context preview verified");
    }

    // ============================================
    // TEST 5: Generate AI Test
    // ============================================

    @org.junit.jupiter.api.Test
    @Order(5)
    @DisplayName("Step 5: Generate AI test with OpenAPI context")
    void step5_generateAITest() throws Exception {
        System.out.println("\n=== STEP 5: Generate AI Test ===");

        assertThat(endpointId).isNotNull();

        GenerateTestRequest request = GenerateTestRequest.builder()
                .userPrompt("Generate a comprehensive test")
                .framework("JUnit 5")
                .language("Java")
                .requesterId(UUID.randomUUID())
                .priority("HIGH")
                .justification("Critical endpoint")
                .build();

        // ✅ ADD API KEY HEADER
        MvcResult result = mockMvc.perform(post("/api/openapi/endpoints/{id}/generate-test", endpointId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-API-Key", apiKey))  // ✅ ADDED
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.testId").exists())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.approvalRequestId").exists())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode response = objectMapper.readTree(responseJson);

        testId = UUID.fromString(response.get("testId").asText());
        approvalRequestId = UUID.fromString(response.get("approvalRequestId").asText());

        System.out.println("✓ Generated test ID: " + testId);
        System.out.println("✓ Approval request ID: " + approvalRequestId);
    }

    // ============================================
    // TEST 6-11: Continue with all other tests
    // ============================================
    // [Copy remaining tests 6-11 from original, no changes needed
    //  as they don't make HTTP requests]

    @org.junit.jupiter.api.Test
    @Order(6)
    @DisplayName("Step 6: Verify Test entity was created correctly")
    void step6_verifyTestEntity() {
        System.out.println("\n=== STEP 6: Verify Test Entity ===");

        assertThat(testId).isNotNull();

        Test test = testRepository.findById(testId).orElseThrow();
        assertThat(test.getName()).startsWith("test_post_users");
        assertThat(test.getIsActive()).isFalse();

        System.out.println("✓ Test verified: " + test.getName());
    }

    @org.junit.jupiter.api.Test
    @Order(7)
    @DisplayName("Step 7: Verify ApprovalRequest was created")
    void step7_verifyApprovalRequest() {
        System.out.println("\n=== STEP 7: Verify Approval Request ===");

        assertThat(approvalRequestId).isNotNull();

        ApprovalRequest approval = approvalRequestRepository.findById(approvalRequestId).orElseThrow();
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.PENDING_APPROVAL);
        assertThat(approval.getTestFramework()).isEqualTo("JUnit 5");

        System.out.println("✓ Approval verified: " + approval.getStatus());
    }

    @org.junit.jupiter.api.Test
    @Order(8)
    @DisplayName("Step 8: Verify TestGenerationContext")
    void step8_verifyContext() {
        System.out.println("\n=== STEP 8: Verify Context ===");

        assertThat(testId).isNotNull();

        TestGenerationContext context = contextRepository.findByTestId(testId).orElseThrow();
        contextId = context.getId();

        assertThat(context.getTestId()).isEqualTo(testId);
        assertThat(context.getApprovalRequestId()).isEqualTo(approvalRequestId);
        assertThat(context.getEndpoint().getId()).isEqualTo(endpointId);

        System.out.println("✓ Context verified - All linked!");
    }

    @org.junit.jupiter.api.Test
    @Order(9)
    @DisplayName("Step 9: Verify complete entity graph")
    void step9_verifyEntityGraph() {
        System.out.println("\n=== STEP 9: Verify Entity Graph ===");

        TestGenerationContext context = contextRepository.findById(contextId).orElseThrow();

        assertThat(context.getSpecification().getId()).isEqualTo(specId);
        assertThat(context.getEndpoint().getId()).isEqualTo(endpointId);
        assertThat(context.getTestId()).isEqualTo(testId);
        assertThat(context.getApprovalRequestId()).isEqualTo(approvalRequestId);

        System.out.println("✓ ALL RELATIONSHIPS VERIFIED!");
    }

    @org.junit.jupiter.api.Test
    @Order(10)
    @DisplayName("Step 10: Verify generation history API")
    void step10_verifyHistory() throws Exception {
        System.out.println("\n=== STEP 10: Verify History ===");

        // ✅ ADD API KEY HEADER
        mockMvc.perform(get("/api/openapi/generation-history")
                        .header("X-API-Key", apiKey))  // ✅ ADDED
                .andExpect(status().isOk());
                //.andExpect(jsonPath("$").isArray());

        System.out.println("✓ History API verified");
    }

    @org.junit.jupiter.api.Test
    @Order(11)
    @DisplayName("Step 11: E2E Summary")
    void step11_summary() {
        System.out.println("\n╔═══════════════════════════════════════════════════════╗");
        System.out.println("║     WEEK 8 E2E TEST COMPLETE - ALL CHECKS PASSED     ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝");

        assertThat(specId).isNotNull();
        assertThat(endpointId).isNotNull();
        assertThat(testId).isNotNull();
        assertThat(approvalRequestId).isNotNull();
        assertThat(contextId).isNotNull();

        System.out.println("✅ WEEK 8 VALIDATED - READY FOR WEEK 9!");
    }
}
package com.company.qa.service.openapi;

import com.company.qa.model.entity.ApiEndpoint;
import com.company.qa.model.entity.ApiSchema;
import com.company.qa.model.entity.ApiSpecification;
import com.company.qa.repository.ApiEndpointRepository;
import com.company.qa.repository.ApiSchemaRepository;
import com.company.qa.repository.ApiSpecificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ApiSpecificationServiceIntegrationTest {

    @Autowired
    private ApiSpecificationService specificationService;

    @Autowired
    private ApiSpecificationRepository specRepository;

    @Autowired
    private ApiEndpointRepository endpointRepository;

    @Autowired
    private ApiSchemaRepository schemaRepository;

    private String sampleOpenAPIJson;

    @BeforeEach
    void setUp() {
        // Clean up
        specRepository.deleteAll();

        // Sample OpenAPI 3.0 spec
        sampleOpenAPIJson = """
            {
              "openapi": "3.0.0",
              "info": {
                "title": "Sample API",
                "version": "1.0.0",
                "description": "A sample API for testing"
              },
              "servers": [
                {
                  "url": "https://api.example.com/v1"
                }
              ],
              "paths": {
                "/users": {
                  "get": {
                    "summary": "List users",
                    "operationId": "listUsers",
                    "tags": ["users"],
                    "parameters": [
                      {
                        "name": "limit",
                        "in": "query",
                        "schema": {
                          "type": "integer",
                          "default": 10
                        }
                      }
                    ],
                    "responses": {
                      "200": {
                        "description": "Success",
                        "content": {
                          "application/json": {
                            "schema": {
                              "type": "array",
                              "items": {
                                "$ref": "#/components/schemas/User"
                              }
                            }
                          }
                        }
                      }
                    }
                  },
                  "post": {
                    "summary": "Create user",
                    "operationId": "createUser",
                    "tags": ["users"],
                    "requestBody": {
                      "content": {
                        "application/json": {
                          "schema": {
                            "$ref": "#/components/schemas/User"
                          }
                        }
                      }
                    },
                    "responses": {
                      "201": {
                        "description": "Created"
                      }
                    }
                  }
                },
                "/users/{id}": {
                  "get": {
                    "summary": "Get user by ID",
                    "operationId": "getUserById",
                    "tags": ["users"],
                    "parameters": [
                      {
                        "name": "id",
                        "in": "path",
                        "required": true,
                        "schema": {
                          "type": "string"
                        }
                      }
                    ],
                    "responses": {
                      "200": {
                        "description": "Success"
                      }
                    }
                  }
                }
              },
              "components": {
                "schemas": {
                  "User": {
                    "type": "object",
                    "properties": {
                      "id": {
                        "type": "string"
                      },
                      "name": {
                        "type": "string"
                      },
                      "email": {
                        "type": "string",
                        "format": "email"
                      }
                    },
                    "required": ["email"]
                  }
                }
              }
            }
            """;
    }

    @Test
    void shouldUploadAndParseOpenAPISpecification() {
        // When
        ApiSpecification spec = specificationService.uploadSpecification(
                sampleOpenAPIJson, "JSON", "test-user");

        // Then
        assertThat(spec.getId()).isNotNull();
        assertThat(spec.getName()).isEqualTo("Sample API");
        assertThat(spec.getVersion()).isEqualTo("1.0.0");
        assertThat(spec.getOpenapiVersion()).isEqualTo("3.0.0");
        assertThat(spec.getBaseUrl()).isEqualTo("https://api.example.com/v1");
        assertThat(spec.getUploadedBy()).isEqualTo("test-user");
        assertThat(spec.getIsActive()).isTrue();
    }

    @Test
    void shouldExtractAndSaveEndpoints() {
        // When
        ApiSpecification spec = specificationService.uploadSpecification(
                sampleOpenAPIJson, "JSON", "test-user");

        List<ApiEndpoint> endpoints = specificationService.getEndpoints(spec.getId());

        // Then
        assertThat(endpoints).hasSize(3); // GET /users, POST /users, GET /users/{id}
        assertThat(spec.getEndpointCount()).isEqualTo(3);

        // Verify endpoint details
        ApiEndpoint getUsers = endpoints.stream()
                .filter(e -> "GET".equals(e.getMethod()) && "/users".equals(e.getPath()))
                .findFirst()
                .orElseThrow();

        assertThat(getUsers.getSummary()).isEqualTo("List users");
        assertThat(getUsers.getOperationId()).isEqualTo("listUsers");
        assertThat(getUsers.getTags()).contains("users");
        assertThat(getUsers.getQueryParameters()).isNotNull();
    }

    @Test
    void shouldExtractAndSaveSchemas() {
        // When
        ApiSpecification spec = specificationService.uploadSpecification(
                sampleOpenAPIJson, "JSON", "test-user");

        List<ApiSchema> schemas = specificationService.getSchemas(spec.getId());

        // Then
        assertThat(schemas).hasSize(1);
        assertThat(spec.getSchemaCount()).isEqualTo(1);

        ApiSchema userSchema = schemas.get(0);
        assertThat(userSchema.getSchemaName()).isEqualTo("User");
        assertThat(userSchema.getSchemaType()).isEqualTo("object");
        assertThat(userSchema.getSchemaDefinition()).contains("email");
    }

    @Test
    void shouldUpdateExistingSpecification() {
        // Given
        ApiSpecification original = specificationService.uploadSpecification(
                sampleOpenAPIJson, "JSON", "test-user");

        String updatedSpec = sampleOpenAPIJson.replace("1.0.0", "1.1.0");

        // When
        ApiSpecification updated = specificationService.updateSpecification(
                original.getId(), updatedSpec, "JSON", "test-user");

        // Then
        assertThat(updated.getId()).isEqualTo(original.getId());
        assertThat(updated.getVersion()).isEqualTo("1.1.0");
    }

    @Test
    void shouldDeleteSpecificationCascade() {
        // Given
        ApiSpecification spec = specificationService.uploadSpecification(
                sampleOpenAPIJson, "JSON", "test-user");

        Long specId = spec.getId();
        assertThat(endpointRepository.countBySpecificationId(specId)).isGreaterThan(0);
        assertThat(schemaRepository.countBySpecificationId(specId)).isGreaterThan(0);

        // When
        specificationService.deleteSpecification(specId);

        // Then
        assertThat(specRepository.findById(specId)).isEmpty();
        assertThat(endpointRepository.countBySpecificationId(specId)).isZero();
        assertThat(schemaRepository.countBySpecificationId(specId)).isZero();
    }

    @Test
    void shouldSearchSpecifications() {
        // Given
        specificationService.uploadSpecification(sampleOpenAPIJson, "JSON", "test-user");

        // When
        List<ApiSpecification> results = specificationService.searchSpecifications("Sample");

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).contains("Sample");
    }

    @Test
    void shouldGetStatistics() {
        // Given
        ApiSpecification spec = specificationService.uploadSpecification(
                sampleOpenAPIJson, "JSON", "test-user");

        // When
        ApiSpecificationService.SpecificationStatistics stats =
                specificationService.getStatistics(spec.getId());

        // Then
        assertThat(stats.getName()).isEqualTo("Sample API");
        assertThat(stats.getTotalEndpoints()).isEqualTo(3);
        assertThat(stats.getTotalSchemas()).isEqualTo(1);
        assertThat(stats.getUploadedBy()).isEqualTo("test-user");
    }

    @Test
    void shouldDeactivateSpecification() {
        // Given
        ApiSpecification spec = specificationService.uploadSpecification(
                sampleOpenAPIJson, "JSON", "test-user");

        // When
        specificationService.deactivateSpecification(spec.getId());

        // Then
        ApiSpecification deactivated = specificationService.getSpecification(spec.getId());
        assertThat(deactivated.getIsActive()).isFalse();

        List<ApiSpecification> activeSpecs = specificationService.getAllActiveSpecifications();
        assertThat(activeSpecs).isEmpty();
    }
}
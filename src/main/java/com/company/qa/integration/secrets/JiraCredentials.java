package com.company.qa.integration.secrets;

import lombok.Builder;
import lombok.Value;

/**
 * Immutable JIRA credentials retrieved from AWS Secrets Manager.
 * Never log or persist these values!
 */
@Value
@Builder
public class JiraCredentials {
    /**
     * JIRA user email (used for Basic auth with API token)
     */
    String email;

    /**
     * JIRA API token (NOT password!)
     * Generated from: https://id.atlassian.com/manage-profile/security/api-tokens
     */
    String apiToken;

    /**
     * Optional: JIRA Cloud ID for advanced APIs
     */
    String cloudId;

    /**
     * Returns HTTP Basic Auth header value.
     * Format: "Basic base64(email:apiToken)"
     */
    public String toBasicAuthHeader() {
        String credentials = email + ":" + apiToken;
        return "Basic " + java.util.Base64.getEncoder()
                .encodeToString(credentials.getBytes());
    }
}
package com.company.qa.service.playwright;

import com.company.qa.model.intent.IntentActionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Provides structured framework constraints for AI prompt injection.
 * Single source of truth for what the AI can and cannot do.
 *
 * @since Zero-Hallucination Pipeline
 */
@Slf4j
@Service
public class FrameworkCapabilityService {

    @Value("${playwright.renderer.base-class:com.company.qa.playwright.BasePlaywrightTest}")
    private String baseClass;

    @Value("${playwright.renderer.base-package:com.company.qa.playwright.generated}")
    private String basePackage;

    /**
     * Build capability context string for AI prompt injection.
     * Called by PlaywrightContextBuilder.buildContext() when intent mode is enabled.
     */
    public String getCapabilitiesForPrompt() {
        StringBuilder sb = new StringBuilder();

        sb.append("=== Framework Capabilities ===\n\n");

        // Base class info
        sb.append("Base class: ").append(baseClass).append("\n");
        sb.append("Provided fields: page (Page), browser (Browser) — already initialized\n");
        sb.append("Package: ").append(basePackage).append("\n\n");

        // Supported actions
        sb.append("Supported actions:\n");
        sb.append(Arrays.stream(IntentActionType.values())
                .map(a -> "  " + a.name() + " — " + a.getDisplayName())
                .collect(Collectors.joining("\n")));
        sb.append("\n\n");

        // Locator format
        sb.append("Locator format: strategy=value\n");
        sb.append("Supported strategies: role, label, text, testid, css, xpath, id, name, class\n");
        sb.append("Examples:\n");
        sb.append("  testid=username\n");
        sb.append("  role=button[name='Login']\n");
        sb.append("  css=[data-test=\"login-button\"]\n");
        sb.append("  label=Email Address\n");
        sb.append("  #user-name                    (no prefix = CSS default)\n\n");

        // Forbidden patterns
        sb.append("FORBIDDEN (renderer will reject):\n");
        sb.append("  - Playwright.create() — handled by base class\n");
        sb.append("  - Thread.sleep() — Playwright auto-waits\n");
        sb.append("  - Browser/Page lifecycle — handled by base class\n");
        sb.append("  - Raw Java code — only TestIntent JSON accepted\n");

        return sb.toString();
    }

    public String getBaseClass() {
        return baseClass;
    }

    public String getBasePackage() {
        return basePackage;
    }
}
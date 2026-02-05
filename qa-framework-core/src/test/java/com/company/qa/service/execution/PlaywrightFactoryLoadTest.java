package com.company.qa.service.execution;

import com.company.qa.QaFrameworkApplication;
import com.company.qa.config.PlaywrightProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

/**
 * Verify PlaywrightFactory loads in Spring context
 */
@SpringBootTest(classes = {
        PlaywrightFactory.class,
        PlaywrightProperties.class
})
@ActiveProfiles("test")
class PlaywrightFactoryLoadTest {

    @Autowired
    private PlaywrightFactory factory;

    @Test
    void shouldLoadFactory() {
        // Then
        assertThat(factory).isNotNull();
        assertThat(factory.isEnabled()).isTrue();
    }

    @Test
    void shouldGetAvailableBrowsers() {
        // When
        String[] browsers = factory.getAvailableBrowsers();

        // Then
        assertThat(browsers).containsExactly("CHROMIUM", "FIREFOX", "WEBKIT");
    }
}
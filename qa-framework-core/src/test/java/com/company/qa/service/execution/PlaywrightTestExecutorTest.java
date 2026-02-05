package com.company.qa.service.execution;

import com.company.qa.QaFrameworkApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

/**
 * Basic test to verify PlaywrightTestExecutor loads
 */
@SpringBootTest(classes = QaFrameworkApplication.class)
@ActiveProfiles("test")
class PlaywrightTestExecutorTest {

    @Autowired
    private PlaywrightTestExecutor executor;

    @Test
    void shouldLoadExecutor() {
        assertThat(executor).isNotNull();
    }
}
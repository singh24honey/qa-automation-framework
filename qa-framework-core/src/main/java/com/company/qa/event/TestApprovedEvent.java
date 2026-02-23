package com.company.qa.event;

import java.util.UUID;

public record TestApprovedEvent(
        UUID testId,
        String testName
) {}
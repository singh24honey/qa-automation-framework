package com.company.qa.model.entity;

import com.company.qa.model.enums.Priority;
import com.company.qa.model.enums.TestFramework;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "tests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Test extends BaseEntity {

    //have added this manually to pass compile time error in jacabo test
    @Column(nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TestFramework framework;

    @Column(nullable = false)
    private String language;

    @Enumerated(EnumType.STRING)
    private Priority priority;

    @Column(name = "estimated_duration")
    private Integer estimatedDuration;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(columnDefinition = "TEXT")
    private String content;
}
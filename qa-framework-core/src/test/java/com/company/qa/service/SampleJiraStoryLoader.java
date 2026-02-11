package com.company.qa.service;

import com.company.qa.model.entity.JiraStory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sample JIRA Story Loader for Week 15 Testing
 *
 * Loads pre-defined Sauce Demo JIRA stories from JSON file.
 * Used for testing the full pipeline without real JIRA API calls.
 *
 * In production, stories would come from JiraStoryService.fetchStory()
 * For Week 15 testing, we use these sample stories to validate:
 * - JIRA context building
 * - Element registry integration
 * - PlaywrightAgent test generation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SampleJiraStoryLoader {

    private final ObjectMapper objectMapper;

    private List<JiraStory> cachedStories;

    /**
     * Load all sample Sauce Demo JIRA stories from JSON file
     */
    public List<JiraStory> loadAllSauceDemoStories() throws IOException {
        if (cachedStories != null) {
            log.debug("Returning {} cached sample stories", cachedStories.size());
            return cachedStories;
        }

        log.info("Loading sample Sauce Demo JIRA stories from JSON");

        ClassPathResource resource = new ClassPathResource(
                "sample-data/saucedemo-jira-stories.json");

        try (InputStream is = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(is);
            JsonNode storiesNode = root.get("stories");

            List<JiraStory> stories = new ArrayList<>();

            for (JsonNode storyNode : storiesNode) {
                JiraStory story = parseStoryFromJson(storyNode);
                stories.add(story);
            }

            cachedStories = stories;
            log.info("Loaded {} sample JIRA stories", stories.size());
            return stories;
        }
    }

    /**
     * Get a single story by JIRA key
     */
    public Optional<JiraStory> getStoryByKey(String jiraKey) throws IOException {
        List<JiraStory> stories = loadAllSauceDemoStories();
        return stories.stream()
                .filter(s -> s.getJiraKey().equals(jiraKey))
                .findFirst();
    }

    /**
     * Get stories by label
     */
    public List<JiraStory> getStoriesByLabel(String label) throws IOException {
        List<JiraStory> stories = loadAllSauceDemoStories();
        return stories.stream()
                .filter(s -> s.getLabels() != null )
                .toList();
    }

    /**
     * Parse JiraStory entity from JSON node
     */
    private JiraStory parseStoryFromJson(JsonNode node) {
        JiraStory story = new JiraStory();
        story.setId(UUID.randomUUID());
        story.setJiraKey(node.get("jiraKey").asText());
        story.setSummary(node.get("summary").asText());
        story.setDescription(node.get("description").asText());
        story.setStoryType(node.get("storyType").asText());
        story.setStatus(node.get("status").asText());
        story.setPriority(node.get("priority").asText());
        story.setAcceptanceCriteria(node.get("acceptanceCriteria").asText());

        // Parse labels
        if (node.has("labels")) {
            String[] labels = new String[node.get("labels").size()];
            //node.get("labels").forEach(l -> labels.add(l.asText()));
            story.setLabels(labels);
        }

        // Parse components
        if (node.has("components")) {
            String[] components = new String[node.get("components").size()];
           // node.get("components").forEach(c -> components.add(c.asText()));
            story.setComponents(components);
        }

        // Set audit fields
        story.setCreatedAt(
                LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault())
        );
        story.setUpdatedAt(LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()));
        //story.setLastFetchedAt(Instant.now());
        //story.setCreatedBy("SAMPLE_DATA");

        return story;
    }
}
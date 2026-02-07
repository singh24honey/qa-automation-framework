package com.company.qa.service.playwright;

import com.company.qa.model.playwright.PageObjectInfo;
import com.company.qa.model.playwright.PageObjectInfo.MethodParameter;
import com.company.qa.model.playwright.PageObjectInfo.PageObjectMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service to scan and registry Playwright Page Objects.
 *
 * Discovers:
 * - All Page Object classes
 * - Public methods in each Page Object
 * - Method signatures for AI context
 */
@Service
@Slf4j
public class PageObjectRegistryService {

    @Value("${playwright.page-objects.scan-path:src/test/java}")
    private String scanPath;

    @Value("${playwright.page-objects.package-pattern:.*\\.pages\\..*}")
    private String packagePattern;

    private Map<String, PageObjectInfo> registry = new HashMap<>();

    /**
     * Scan for Page Objects and build registry.
     */
    public void scanPageObjects() {
        log.info("Scanning for Page Objects in: {}", scanPath);

        try {
            Path basePath = Paths.get(scanPath);

            if (!Files.exists(basePath)) {
                log.warn("Scan path does not exist: {}", scanPath);
                registry = new HashMap<>();
                return;
            }

            List<Path> javaFiles = findJavaFiles(basePath);
            log.info("Found {} Java files to scan", javaFiles.size());

            for (Path javaFile : javaFiles) {
                try {
                    scanFile(javaFile);
                } catch (Exception e) {
                    log.warn("Failed to scan file {}: {}", javaFile, e.getMessage());
                }
            }

            log.info("Page Object Registry built: {} Page Objects found", registry.size());

        } catch (IOException e) {
            log.error("Failed to scan Page Objects: {}", e.getMessage(), e);
            registry = new HashMap<>();
        }
    }

    /**
     * Find all Java files in directory.
     */
    private List<Path> findJavaFiles(Path basePath) throws IOException {
        try (Stream<Path> walk = Files.walk(basePath)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> matchesPackagePattern(path))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Check if file matches package pattern (e.g., contains ".pages.").
     */
    private boolean matchesPackagePattern(Path path) {
        String pathStr = path.toString().replace('\\', '/');
        return pathStr.matches(packagePattern) || pathStr.contains("/pages/");
    }

    /**
     * Scan single Java file for Page Object information.
     */
    private void scanFile(Path javaFile) throws IOException {
        String content = Files.readString(javaFile);

        // Check if it's a Page Object class
        if (!isPageObjectClass(content)) {
            return;
        }

        String className = extractClassName(content);
        if (className == null) {
            return;
        }

        String packageName = extractPackageName(content);
        String fullClassName = packageName != null ? packageName + "." + className : className;

        PageObjectInfo pageObject = PageObjectInfo.builder()
                .className(fullClassName)
                .simpleName(className)
                .packageName(packageName)
                .pageUrl(extractPageUrl(content))
                .methods(extractPublicMethods(content))
                .filePath(javaFile.toString())
                .build();

        registry.put(className, pageObject);

        log.debug("Registered Page Object: {} with {} methods",
                className, pageObject.getMethods().size());
    }

    /**
     * Check if class is a Page Object (simple heuristic).
     */
    private boolean isPageObjectClass(String content) {
        // Look for common Page Object patterns
        return content.contains("extends BasePage")
                || content.contains("implements Page")
                || content.contains("class") && content.contains("Page")
                || content.contains("import com.microsoft.playwright.Page");
    }

    /**
     * Extract class name from Java source.
     */
    private String extractClassName(String content) {
        Pattern pattern = Pattern.compile("public\\s+class\\s+(\\w+)");
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * Extract package name from Java source.
     */
    private String extractPackageName(String content) {
        Pattern pattern = Pattern.compile("package\\s+([\\w.]+);");
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * Extract page URL from annotations or constants.
     */
    private String extractPageUrl(String content) {
        // Look for @PageUrl or URL constant
        Pattern urlPattern = Pattern.compile("@PageUrl\\(\"([^\"]+)\"\\)");
        Matcher matcher = urlPattern.matcher(content);

        if (matcher.find()) {
            return matcher.group(1);
        }

        // Look for public static final String URL = "..."
        Pattern constPattern = Pattern.compile("public\\s+static\\s+final\\s+String\\s+URL\\s*=\\s*\"([^\"]+)\"");
        matcher = constPattern.matcher(content);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * Extract public methods from Java source.
     */
    private List<PageObjectMethod> extractPublicMethods(String content) {
        List<PageObjectMethod> methods = new ArrayList<>();

        // Pattern to match public methods
        // Example: public void login(String email, String password)
        Pattern pattern = Pattern.compile(
                "public\\s+(\\w+(?:<[^>]+>)?)\\s+(\\w+)\\s*\\(([^)]*)\\)"
        );

        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            String returnType = matcher.group(1);
            String methodName = matcher.group(2);
            String paramsStr = matcher.group(3).trim();

            // Skip constructors
            if (methodName.equals(extractClassName(content))) {
                continue;
            }

            List<MethodParameter> parameters = parseParameters(paramsStr);
            String signature = buildMethodSignature(methodName, returnType, parameters);

            PageObjectMethod method = PageObjectMethod.builder()
                    .name(methodName)
                    .returnType(returnType)
                    .parameters(parameters)
                    .signature(signature)
                    .build();

            methods.add(method);
        }

        return methods;
    }

    /**
     * Parse method parameters.
     */
    private List<MethodParameter> parseParameters(String paramsStr) {
        if (paramsStr.isEmpty()) {
            return Collections.emptyList();
        }

        List<MethodParameter> parameters = new ArrayList<>();
        String[] params = paramsStr.split(",");

        for (String param : params) {
            param = param.trim();
            if (param.isEmpty()) {
                continue;
            }

            // Split "Type name" or "Type<Generic> name"
            String[] parts = param.split("\\s+");
            if (parts.length >= 2) {
                String type = parts[0];
                String name = parts[parts.length - 1];

                parameters.add(MethodParameter.builder()
                        .type(type)
                        .name(name)
                        .build());
            }
        }

        return parameters;
    }

    /**
     * Build method signature for AI context.
     */
    private String buildMethodSignature(String methodName, String returnType,
                                        List<MethodParameter> parameters) {
        StringBuilder sig = new StringBuilder();
        sig.append(returnType).append(" ").append(methodName).append("(");

        for (int i = 0; i < parameters.size(); i++) {
            MethodParameter param = parameters.get(i);
            sig.append(param.getType()).append(" ").append(param.getName());

            if (i < parameters.size() - 1) {
                sig.append(", ");
            }
        }

        sig.append(")");
        return sig.toString();
    }

    // ============================================================
    // PUBLIC API
    // ============================================================

    /**
     * Get all registered Page Objects.
     */
    public List<PageObjectInfo> getAllPageObjects() {
        return new ArrayList<>(registry.values());
    }

    /**
     * Get specific Page Object by name.
     */
    public Optional<PageObjectInfo> getPageObject(String className) {
        return Optional.ofNullable(registry.get(className));
    }

    /**
     * Search Page Objects by keyword.
     */
    public List<PageObjectInfo> searchPageObjects(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String search = keyword.toLowerCase();

        return registry.values().stream()
                .filter(po -> po.getSimpleName().toLowerCase().contains(search)
                        || (po.getPageUrl() != null && po.getPageUrl().toLowerCase().contains(search)))
                .collect(Collectors.toList());
    }

    /**
     * Get AI context for Page Objects.
     * Returns formatted list of available Page Objects and their methods.
     */
    public String getContextForAIPrompt() {
        if (registry.isEmpty()) {
            return "=== Available Page Objects ===\n\nNo Page Objects found.\n";
        }

        StringBuilder context = new StringBuilder();
        context.append("=== Available Page Objects ===\n\n");

        registry.values().forEach(pageObject -> {
            context.append(String.format("- %s", pageObject.getSimpleName()));

            if (pageObject.getPageUrl() != null) {
                context.append(String.format(" (%s)", pageObject.getPageUrl()));
            }

            context.append("\n  Methods:\n");

            pageObject.getMethods().forEach(method -> {
                context.append(String.format("    - %s\n", method.getSignature()));
            });

            context.append("\n");
        });

        return context.toString();
    }

    /**
     * Get statistics about registered Page Objects.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("pageObjectCount", registry.size());

        int totalMethods = registry.values().stream()
                .mapToInt(po -> po.getMethods().size())
                .sum();
        stats.put("totalMethods", totalMethods);

        long withUrls = registry.values().stream()
                .filter(po -> po.getPageUrl() != null)
                .count();
        stats.put("pageObjectsWithUrls", withUrls);

        return stats;
    }

    /**
     * Reload registry by rescanning.
     */
    public void reload() {
        log.info("Reloading Page Object Registry...");
        registry.clear();
        scanPageObjects();
    }
}
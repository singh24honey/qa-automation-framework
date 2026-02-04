package com.company.qa.service.git;

import com.company.qa.exception.GitOperationException;
import com.company.qa.model.entity.GitConfiguration;
import com.company.qa.model.enums.RepositoryType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory to get the appropriate Git provider service based on repository type
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GitProviderFactory {

    private final List<GitProviderService> providers;

    /**
     * Get the appropriate provider service for the given configuration
     *
     * @param config Git configuration
     * @return Provider service for the repository type
     * @throws GitOperationException if no provider found
     */
    public GitProviderService getProvider(GitConfiguration config) {
        RepositoryType repoType = config.getRepositoryType();

        log.debug("Getting Git provider for type: {}", repoType);

        return providers.stream()
                .filter(provider -> provider.getSupportedType() == repoType)
                .findFirst()
                .orElseThrow(() -> new GitOperationException(
                        "No Git provider found for repository type: " + repoType
                ));
    }

    /**
     * Get provider by repository type
     *
     * @param repositoryType Repository type
     * @return Provider service
     */
    public GitProviderService getProvider(RepositoryType repositoryType) {
        return providers.stream()
                .filter(provider -> provider.getSupportedType() == repositoryType)
                .findFirst()
                .orElseThrow(() -> new GitOperationException(
                        "No Git provider found for repository type: " + repositoryType
                ));
    }

    /**
     * Get all available providers
     *
     * @return Map of repository type to provider
     */
    public Map<RepositoryType, GitProviderService> getAllProviders() {
        return providers.stream()
                .collect(Collectors.toMap(
                        GitProviderService::getSupportedType,
                        Function.identity()
                ));
    }

    /**
     * Check if provider is available for the given type
     *
     * @param repositoryType Repository type
     * @return true if provider is available
     */
    public boolean isProviderAvailable(RepositoryType repositoryType) {
        return providers.stream()
                .anyMatch(provider -> provider.getSupportedType() == repositoryType);
    }
}
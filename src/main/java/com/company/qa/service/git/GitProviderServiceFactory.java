package com.company.qa.service.git;

import com.company.qa.exception.GitOperationException;
import com.company.qa.model.entity.GitConfiguration;
import com.company.qa.model.enums.RepositoryType;
import com.company.qa.service.git.provider.GitHubProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Factory to get the appropriate Git provider service
 * Currently supports GitHub, can be extended for GitLab and Bitbucket
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GitProviderServiceFactory {

    private final GitHubProviderService gitHubProviderService;
    // Add more providers here as needed:
    // private final GitLabProviderService gitLabProviderService;
    // private final BitbucketProviderService bitbucketProviderService;

    /**
     * Get the appropriate provider for the given configuration
     */
    public GitProviderService getProvider(GitConfiguration config) {
        RepositoryType repoType = config.getRepositoryType();

        log.debug("Getting Git provider for type: {}", repoType);

        return switch (repoType) {
            case GITHUB -> gitHubProviderService;
            case GITLAB -> throw new GitOperationException(
                    "GitLab provider not yet implemented. Please use GitHub for now."
            );
            case BITBUCKET -> throw new GitOperationException(
                    "Bitbucket provider not yet implemented. Please use GitHub for now."
            );
            default -> throw new GitOperationException(
                    "Unknown repository type: " + repoType
            );
        };
    }

    /**
     * Check if a provider is available for the given type
     */
    public boolean isProviderAvailable(RepositoryType repositoryType) {
        return repositoryType == RepositoryType.GITHUB;
        // Add more as you implement them:
        // return repositoryType == RepositoryType.GITHUB ||
        //        repositoryType == RepositoryType.GITLAB;
    }
}
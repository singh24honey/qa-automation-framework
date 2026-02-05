package com.company.qa.repository;

import com.company.qa.model.entity.AIBudget;
import com.company.qa.model.enums.BudgetScopeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AIBudgetRepository extends JpaRepository<AIBudget, UUID> {

    // Find active budgets
    List<AIBudget> findByActiveTrue();

    // Find by scope type
    List<AIBudget> findByScopeTypeAndActiveTrue(BudgetScopeType scopeType);

    // Find by scope type and scope ID
    Optional<AIBudget> findByScopeTypeAndScopeIdAndActiveTrue(
            BudgetScopeType scopeType, String scopeId);

    // Find global budget
    @Query("SELECT b FROM AIBudget b WHERE b.scopeType = 'GLOBAL' AND b.active = true")
    Optional<AIBudget> findGlobalBudget();

    // Find user budget
    @Query("SELECT b FROM AIBudget b WHERE b.scopeType = 'USER' AND b.scopeId = :userId AND b.active = true")
    Optional<AIBudget> findUserBudget(@Param("userId") String userId);

    // Find team budget
    @Query("SELECT b FROM AIBudget b WHERE b.scopeType = 'TEAM' AND b.scopeId = :teamId AND b.active = true")
    Optional<AIBudget> findTeamBudget(@Param("teamId") String teamId);
}
package com.company.qa.execution.api;

import com.company.qa.execution.reconciliation.ExternalExecutionReconciliationService;
import com.company.qa.execution.reconciliation.ExternalExecutionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/executions/external")
@RequiredArgsConstructor
public class ExternalExecutionCallbackController {

    private final ExternalExecutionReconciliationService reconciliationService;

    @PostMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestBody ExternalExecutionResult result) {

        reconciliationService.reconcile(result);
        return ResponseEntity.ok().build();
    }
}
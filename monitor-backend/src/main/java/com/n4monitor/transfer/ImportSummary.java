package com.n4monitor.transfer;

import java.util.List;

public record ImportSummary(
        boolean valid,
        boolean dryRun,
        boolean committed,
        int sourceFileCount,
        int sourceRowCount,
        int deviceTemplateCreated,
        int deviceTemplateReused,
        int pointTemplateCreated,
        int pointTemplateReused,
        int pointTemplateBackfilled,
        int deviceDeploymentCreated,
        int deviceDeploymentReused,
        int pointDeploymentCreated,
        int pointDeploymentReused,
        int enumRuleCreated,
        int numericRuleCreated,
        int navigationGroupCreated,
        List<String> navigationGroupNames,
        int skippedRowCount,
        List<ImportIssue> issues) {

    public ImportSummary {
        navigationGroupNames = List.copyOf(navigationGroupNames);
        issues = List.copyOf(issues);
    }
}

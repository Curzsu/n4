package com.n4monitor.transfer;

import com.n4monitor.transfer.deployment.DeploymentPreviewItem;
import com.n4monitor.transfer.template.TemplatePreviewItem;

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
        List<ImportIssue> issues,
        List<TemplatePreviewItem> templatePreviews,
        List<DeploymentPreviewItem> deploymentPreviews) {

    public ImportSummary {
        navigationGroupNames = navigationGroupNames == null ? List.of() : List.copyOf(navigationGroupNames);
        issues = issues == null ? List.of() : List.copyOf(issues);
        templatePreviews = templatePreviews == null ? List.of() : List.copyOf(templatePreviews);
        deploymentPreviews = deploymentPreviews == null ? List.of() : List.copyOf(deploymentPreviews);
    }

    public ImportSummary(
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
        this(
                valid,
                dryRun,
                committed,
                sourceFileCount,
                sourceRowCount,
                deviceTemplateCreated,
                deviceTemplateReused,
                pointTemplateCreated,
                pointTemplateReused,
                pointTemplateBackfilled,
                deviceDeploymentCreated,
                deviceDeploymentReused,
                pointDeploymentCreated,
                pointDeploymentReused,
                enumRuleCreated,
                numericRuleCreated,
                navigationGroupCreated,
                navigationGroupNames,
                skippedRowCount,
                issues,
                List.of(),
                List.of());
    }
}

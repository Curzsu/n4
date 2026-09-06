package com.n4monitor.transfer;

import com.n4monitor.transfer.deployment.DeploymentPreviewItem;
import com.n4monitor.transfer.template.TemplatePreviewItem;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ImportStats {

    private final boolean dryRun;
    private int sourceFileCount;
    private int sourceRowCount;
    private int deviceTemplateCreated;
    private int deviceTemplateReused;
    private int pointTemplateCreated;
    private int pointTemplateReused;
    private int pointTemplateBackfilled;
    private int deviceDeploymentCreated;
    private int deviceDeploymentReused;
    private int pointDeploymentCreated;
    private int pointDeploymentReused;
    private int enumRuleCreated;
    private int numericRuleCreated;
    private int navigationGroupCreated;
    private final Set<String> navigationGroupNames = new LinkedHashSet<>();
    private int skippedRowCount;
    private boolean committed;
    private final List<ImportIssue> issues = new ArrayList<>();
    private final List<TemplatePreviewItem> templatePreviews = new ArrayList<>();
    private final List<DeploymentPreviewItem> deploymentPreviews = new ArrayList<>();

    public ImportStats(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public boolean dryRun() {
        return dryRun;
    }

    public void sourceFileCount(int value) {
        sourceFileCount = value;
    }

    public void sourceRowCount(int value) {
        sourceRowCount = value;
    }

    public void deviceTemplateCreated() {
        deviceTemplateCreated++;
    }

    public void deviceTemplateReused() {
        deviceTemplateReused++;
    }

    public void pointTemplateCreated() {
        pointTemplateCreated++;
    }

    public void pointTemplateReused() {
        pointTemplateReused++;
    }

    public void pointTemplateBackfilled() {
        pointTemplateBackfilled++;
    }

    public void deviceDeploymentCreated() {
        deviceDeploymentCreated++;
    }

    public void deviceDeploymentReused() {
        deviceDeploymentReused++;
    }

    public void pointDeploymentCreated() {
        pointDeploymentCreated++;
    }

    public void pointDeploymentReused() {
        pointDeploymentReused++;
    }

    public void enumRuleCreated() {
        enumRuleCreated++;
    }

    public void numericRuleCreated() {
        numericRuleCreated++;
    }

    public void navigationGroupCreated(String name) {
        if (name != null && navigationGroupNames.add(name)) {
            navigationGroupCreated++;
        }
    }

    public void skippedRow() {
        skippedRowCount++;
    }

    public void warning(String code, String message, String fileName, Integer rowNumber) {
        issues.add(new ImportIssue("WARNING", code, message, fileName, rowNumber));
    }

    public void error(String code, String message, String fileName, Integer rowNumber) {
        issues.add(new ImportIssue("ERROR", code, message, fileName, rowNumber));
    }

    public void addTemplatePreview(TemplatePreviewItem item) {
        if (item != null) {
            templatePreviews.add(item);
        }
    }

    public void addDeploymentPreview(DeploymentPreviewItem item) {
        if (item != null) {
            deploymentPreviews.add(item);
        }
    }

    public boolean valid() {
        return issues.stream().noneMatch(issue -> "ERROR".equals(issue.severity()));
    }

    public void committed() {
        committed = true;
    }

    public ImportSummary toSummary() {
        return new ImportSummary(
                valid(),
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
                List.copyOf(navigationGroupNames),
                skippedRowCount,
                issues,
                List.copyOf(templatePreviews),
                List.copyOf(deploymentPreviews));
    }
}

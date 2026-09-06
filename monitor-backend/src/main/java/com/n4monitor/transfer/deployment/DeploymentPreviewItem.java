package com.n4monitor.transfer.deployment;

/**
 * Preview summary for a single device deployment during CSV/ZIP import.
 */
public record DeploymentPreviewItem(
        String sourceFile,
        String deviceName,
        String devicePath,
        String systemName,
        String templateName,
        int pointCount,
        String action) {
}

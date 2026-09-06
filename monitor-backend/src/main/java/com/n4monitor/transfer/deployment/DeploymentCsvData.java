package com.n4monitor.transfer.deployment;

import java.util.List;

public record DeploymentCsvData(String fileName, List<DeploymentCsvRow> rows) {

    public DeploymentCsvData {
        rows = List.copyOf(rows);
    }
}

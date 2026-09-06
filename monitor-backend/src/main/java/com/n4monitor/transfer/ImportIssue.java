package com.n4monitor.transfer;

public record ImportIssue(
        String severity,
        String code,
        String message,
        String fileName,
        Integer rowNumber) {
}

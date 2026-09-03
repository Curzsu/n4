package com.n4monitor.transfer.template;

public record TemplatePointData(
        String pointName,
        String n4Name,
        String location,
        String panelName,
        String purpose,
        String type,
        String unit) {
}

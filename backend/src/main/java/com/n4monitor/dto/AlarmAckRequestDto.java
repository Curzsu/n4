package com.n4monitor.dto;

import lombok.Data;

import java.util.List;

/**
 * Request body for acknowledging alarms.
 */
@Data
public class AlarmAckRequestDto {

    /** List of point paths to acknowledge */
    private List<String> paths;

    /** User who acknowledges the alarms */
    private String ackBy;
}

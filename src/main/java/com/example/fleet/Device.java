package com.example.fleet;

import java.time.Instant;

public record Device(String id, String name, String lastHeartbeat, String lastReportedStatus) {
    public Device withHeartbeat(String timestamp, String reportedStatus) {
        return new Device(id, name, timestamp, reportedStatus);
    }
}

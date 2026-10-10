package com.example.fleet;

import java.time.Instant;

public record Device(String id, String name, String lastHeartbeat, String lastReportedStatus) {
}

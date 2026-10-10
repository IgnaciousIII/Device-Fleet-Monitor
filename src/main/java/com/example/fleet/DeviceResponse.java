package com.example.fleet;

public record DeviceResponse(String id, String name, String status, String lastHeartbeat,
                             String lastReportedStatus) {
}

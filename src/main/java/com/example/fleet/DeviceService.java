package com.example.fleet;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DeviceService {
    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(30);
    private final ConcurrentHashMap<String, Device> devices = new ConcurrentHashMap<>();

    public DeviceResponse register(String id, String name) {
        requireText(id, "id");
        requireText(name, "name");
        Device device = new Device(id.trim(), name.trim(), null, null);
        if (devices.putIfAbsent(device.id(), device) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Device already exists: " + device.id());
        }
        return toResponse(device);
    }

    public DeviceResponse heartbeat(String id, Instant timestamp, String reportedStatus) {
        if (timestamp == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "timestamp is required");
        }
        requireText(reportedStatus, "status");
        Device updated = devices.compute(id, (key, current) -> {
            if (current == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found: " + id);
            }
            return current.withHeartbeat(timestamp.toString(), reportedStatus.trim());
        });
        return toResponse(updated);
    }

    public List<DeviceResponse> list() {
        return devices.values().stream()
                .sorted(Comparator.comparing(Device::id))
                .map(this::toResponse)
                .toList();
    }

    public DeviceResponse get(String id) {
        Device device = devices.get(id);
        if (device == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found: " + id);
        }
        return toResponse(device);
    }

    public FleetSummary summary() {
        List<DeviceResponse> current = list();
        long online = current.stream().filter(device -> device.status().equals("ONLINE")).count();
        return new FleetSummary(current.size(), online, current.size() - online);
    }

    private DeviceResponse toResponse(Device device) {
        String status = "OFFLINE";
        if (device.lastHeartbeat() != null) {
            Instant heartbeat = Instant.parse(device.lastHeartbeat());
            if (!heartbeat.isBefore(Instant.now().minus(HEARTBEAT_TIMEOUT))) {
                status = "ONLINE";
            }
        }
        return new DeviceResponse(device.id(), device.name(), status,
                device.lastHeartbeat(), device.lastReportedStatus());
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must not be blank");
        }
    }
}

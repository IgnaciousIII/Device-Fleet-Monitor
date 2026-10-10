package com.example.fleet;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping
public class DeviceController {
    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceResponse register(@RequestBody RegisterDeviceRequest request) {
        return deviceService.register(request.id(), request.name());
    }

    @PostMapping("/devices/{id}/heartbeat")
    public DeviceResponse heartbeat(@PathVariable String id, @RequestBody HeartbeatRequest request) {
        return deviceService.heartbeat(id, request.timestamp(), request.status());
    }

    @GetMapping("/devices")
    public List<DeviceResponse> list() {
        return deviceService.list();
    }

    @GetMapping("/devices/{id}")
    public DeviceResponse get(@PathVariable String id) {
        return deviceService.get(id);
    }

    @GetMapping("/summary")
    public FleetSummary summary() {
        return deviceService.summary();
    }

    public record RegisterDeviceRequest(String id, String name) { }
    public record HeartbeatRequest(Instant timestamp, String status) { }
}

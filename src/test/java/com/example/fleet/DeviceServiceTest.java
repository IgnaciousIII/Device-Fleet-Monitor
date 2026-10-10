package com.example.fleet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceServiceTest {
    @TempDir
    Path temporaryDirectory;

    private JdbcTemplate jdbcTemplate;
    private DeviceService deviceService;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:sqlite:" + temporaryDirectory.resolve("test-fleet.db"));
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE devices (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    last_heartbeat TEXT,
                    last_reported_status TEXT
                )
                """);
        deviceService = new DeviceService(new DeviceRepository(jdbcTemplate));
    }

    @Test
    void registersAndPersistsDeviceInSqlite() {
        DeviceResponse registered = deviceService.register("device-01", "Lab Device 01");

        DeviceResponse loaded = new DeviceService(new DeviceRepository(jdbcTemplate)).get("device-01");
        assertThat(registered.status()).isEqualTo("OFFLINE");
        assertThat(loaded.id()).isEqualTo("device-01");
        assertThat(loaded.name()).isEqualTo("Lab Device 01");
    }

    @Test
    void rejectsDuplicateDeviceIds() {
        deviceService.register("device-01", "Lab Device 01");

        assertThatThrownBy(() -> deviceService.register("device-01", "Another name"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void recordsHeartbeatAndCalculatesOnlineAndOfflineStatus() {
        deviceService.register("device-01", "Lab Device 01");
        DeviceResponse online = deviceService.heartbeat("device-01", Instant.now(), "OK");

        assertThat(online.status()).isEqualTo("ONLINE");
        assertThat(online.lastReportedStatus()).isEqualTo("OK");

        DeviceResponse offline = deviceService.heartbeat(
                "device-01", Instant.now().minusSeconds(31), "OK");
        assertThat(offline.status()).isEqualTo("OFFLINE");
    }

    @Test
    void summarizesOnlineAndOfflineDevices() {
        deviceService.register("device-01", "Online device");
        deviceService.register("device-02", "Offline device");
        deviceService.heartbeat("device-01", Instant.now(), "OK");

        assertThat(deviceService.summary()).isEqualTo(new FleetSummary(2, 1, 1));
    }
}

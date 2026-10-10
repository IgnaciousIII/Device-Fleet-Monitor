package com.example.fleet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

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
    void laterHeartbeatReplacesThePreviouslyStoredHeartbeatAndReportedStatus() {
        deviceService.register("device-01", "Lab Device 01");
        Instant firstTimestamp = Instant.now().minusSeconds(5);
        Instant secondTimestamp = Instant.now();

        deviceService.heartbeat("device-01", firstTimestamp, "OK");
        DeviceResponse updated = deviceService.heartbeat("device-01", secondTimestamp, "  DEGRADED  ");

        assertThat(updated.lastHeartbeat()).isEqualTo(secondTimestamp.toString());
        assertThat(updated.lastReportedStatus()).isEqualTo("DEGRADED");
        assertThat(updated.status()).isEqualTo("ONLINE");
        assertThat(deviceService.get("device-01").lastReportedStatus()).isEqualTo("DEGRADED");
    }

    @Test
    void summarizesOnlineAndOfflineDevices() {
        deviceService.register("device-01", "Online device");
        deviceService.register("device-02", "Offline device");
        deviceService.heartbeat("device-01", Instant.now(), "OK");

        assertThat(deviceService.summary()).isEqualTo(new FleetSummary(2, 1, 1));
    }

    @Test
    void reportsOfflineUntilARecentHeartbeatArrives() {
        deviceService.register("device-01", "Lab Device 01");
        assertThat(deviceService.get("device-01").status()).isEqualTo("OFFLINE");

        deviceService.heartbeat("device-01", Instant.now().minusSeconds(29), "OK");
        assertThat(deviceService.get("device-01").status()).isEqualTo("ONLINE");

        deviceService.heartbeat("device-01", Instant.now().minusSeconds(31), "OK");
        assertThat(deviceService.get("device-01").status()).isEqualTo("OFFLINE");
    }

    @Test
    void rejectsBlankRegistrationFields() {
        assertBadRequest(() -> deviceService.register("  ", "Lab Device"));
        assertBadRequest(() -> deviceService.register("device-01", "  "));
    }

    @Test
    void rejectsMissingHeartbeatFieldsAndUnknownDevices() {
        assertBadRequest(() -> deviceService.heartbeat("device-01", null, "OK"));
        assertBadRequest(() -> deviceService.heartbeat("device-01", Instant.now(), " "));
        assertBadRequest(() -> deviceService.heartbeat("device-01", Instant.now().plusSeconds(1), "OK"));
        assertThatThrownBy(() -> deviceService.heartbeat("missing", Instant.now(), "OK"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void returnsNotFoundWhenGettingAnUnknownDevice() {
        assertThatThrownBy(() -> deviceService.get("missing"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listsDevicesInIdOrderAndSummarizesAnEmptyFleet() {
        assertThat(deviceService.summary()).isEqualTo(new FleetSummary(0, 0, 0));
        deviceService.register("device-02", "Second");
        deviceService.register("device-01", "First");

        assertThat(deviceService.list()).extracting(DeviceResponse::id)
                .containsExactly("device-01", "device-02");
    }

    private void assertBadRequest(ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}

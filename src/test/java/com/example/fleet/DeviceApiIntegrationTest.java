package com.example.fleet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:sqlite:./target/device-fleet-api-test.db")
class DeviceApiIntegrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${local.server.port}")
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM devices");
    }

    @Test
    void supportsTheDeviceLifecycleAndFleetSummaryOverHttp() throws Exception {
        HttpResponse<String> registered = send("POST", "/devices",
                "{\"id\":\"device-01\",\"name\":\"Lab Device 01\"}");
        assertThat(registered.statusCode()).isEqualTo(201);
        assertThat(registered.body()).contains("\"status\":\"OFFLINE\"");

        HttpResponse<String> secondRegistration = send("POST", "/devices",
                "{\"id\":\"device-02\",\"name\":\"Lab Device 02\"}");
        assertThat(secondRegistration.statusCode()).isEqualTo(201);

        String heartbeat = "{\"timestamp\":\"" + Instant.now()
                + "\",\"status\":\"OK\"}";
        HttpResponse<String> recordedHeartbeat = send("POST", "/devices/device-01/heartbeat", heartbeat);
        assertThat(recordedHeartbeat.statusCode()).isEqualTo(200);
        assertThat(recordedHeartbeat.body()).contains("\"status\":\"ONLINE\"")
                .contains("\"lastReportedStatus\":\"OK\"");

        HttpResponse<String> detail = send("GET", "/devices/device-01", null);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.body()).contains("Lab Device 01");

        HttpResponse<String> list = send("GET", "/devices", null);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.body()).contains("device-01", "device-02");

        HttpResponse<String> summary = send("GET", "/summary", null);
        assertThat(summary.statusCode()).isEqualTo(200);
        assertThat(summary.body()).contains("\"total\":2", "\"online\":1", "\"offline\":1");
    }

    @Test
    void returnsExpectedClientErrors() throws Exception {
        String registration = "{\"id\":\"device-01\",\"name\":\"Lab Device 01\"}";
        assertThat(send("POST", "/devices", registration).statusCode()).isEqualTo(201);
        assertThat(send("POST", "/devices", registration).statusCode()).isEqualTo(409);
        assertThat(send("POST", "/devices", "{\"id\":\" \",\"name\":\"Name\"}").statusCode())
                .isEqualTo(400);
        assertThat(send("GET", "/devices/missing", null).statusCode()).isEqualTo(404);
        assertThat(send("POST", "/devices/missing/heartbeat",
                "{\"timestamp\":\"" + Instant.now() + "\",\"status\":\"OK\"}").statusCode())
                .isEqualTo(404);
        assertThat(send("POST", "/devices/device-01/heartbeat", "{\"status\":\"OK\"}").statusCode())
                .isEqualTo(400);
    }

    private HttpResponse<String> send(String method, String path, String jsonBody) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path));
        if (jsonBody == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}

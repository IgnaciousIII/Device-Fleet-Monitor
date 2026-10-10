package com.example.fleet;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DeviceRepository {
    private static final RowMapper<Device> DEVICE_ROW_MAPPER = (resultSet, rowNumber) -> new Device(
            resultSet.getString("id"),
            resultSet.getString("name"),
            resultSet.getString("last_heartbeat"),
            resultSet.getString("last_reported_status"));

    private final JdbcTemplate jdbcTemplate;

    public DeviceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean insert(Device device) {
        int rowsInserted = jdbcTemplate.update(
                "INSERT OR IGNORE INTO devices (id, name) VALUES (?, ?)",
                device.id(), device.name());
        return rowsInserted == 1;
    }

    public boolean updateHeartbeat(String id, String timestamp, String status) {
        return jdbcTemplate.update(
                "UPDATE devices SET last_heartbeat = ?, last_reported_status = ? WHERE id = ?",
                timestamp, status, id) == 1;
    }

    public List<Device> findAll() {
        return jdbcTemplate.query(
                "SELECT id, name, last_heartbeat, last_reported_status FROM devices ORDER BY id",
                DEVICE_ROW_MAPPER);
    }

    public Optional<Device> findById(String id) {
        List<Device> matches = jdbcTemplate.query(
                "SELECT id, name, last_heartbeat, last_reported_status FROM devices WHERE id = ?",
                DEVICE_ROW_MAPPER, id);
        return matches.stream().findFirst();
    }
}

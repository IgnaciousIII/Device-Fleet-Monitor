# Device Fleet Monitor API Reference

Base URL: `http://localhost:8080`

The API accepts and returns JSON. Device connectivity is calculated from the timestamp of the latest heartbeat:

- `ONLINE`: heartbeat is no more than 30 seconds old.
- `OFFLINE`: there is no heartbeat, or the latest heartbeat is more than 30 seconds old.

Heartbeat timestamps must be ISO-8601 instants and must not be in the future. The submitted heartbeat `status` is returned as `lastReportedStatus`; it does not determine connectivity.

## Error response format

API errors use this JSON structure:

```json
{
  "timestamp": "2026-09-21T10:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Device not found: device-99",
  "path": "/devices/device-99"
}
```

`timestamp` is the time the API generated the error response. `status` and `error` describe the HTTP status, `message` explains the failure, and `path` is the request path. Unexpected server errors use a generic message so internal exception details are not returned to clients.

## `POST /devices` — Register a device

Request:

```json
{
  "id": "device-01",
  "name": "Lab Device 01"
}
```

Successful response: `201 Created`

```json
{
  "id": "device-01",
  "name": "Lab Device 01",
  "status": "OFFLINE",
  "lastHeartbeat": null,
  "lastReportedStatus": null
}
```

The `id` and `name` must contain non-whitespace text. Values are trimmed before storage.

| Status | Meaning |
| --- | --- |
| `201 Created` | Device registered. |
| `400 Bad Request` | ID or name is missing or blank. |
| `409 Conflict` | A device with that ID is already registered. |

## `POST /devices/{id}/heartbeat` — Record a heartbeat

Request:

```json
{
  "timestamp": "2026-09-21T10:30:00Z",
  "status": "OK"
}
```

Successful response: `200 OK`

```json
{
  "id": "device-01",
  "name": "Lab Device 01",
  "status": "ONLINE",
  "lastHeartbeat": "2026-09-21T10:30:00Z",
  "lastReportedStatus": "OK"
}
```

The device must already be registered. `timestamp` must be present and not in the future; `status` must contain non-whitespace text. A heartbeat replaces the stored timestamp and reported status.

| Status | Meaning |
| --- | --- |
| `200 OK` | Heartbeat recorded. |
| `400 Bad Request` | Timestamp is missing, invalid, or in the future, or status is missing or blank. |
| `404 Not Found` | Device ID is not registered. |

## `GET /devices` — List registered devices

Returns `200 OK` and a JSON array of device objects. Results are ordered by device ID.

```json
[
  {
    "id": "device-01",
    "name": "Lab Device 01",
    "status": "ONLINE",
    "lastHeartbeat": "2026-09-21T10:30:00Z",
    "lastReportedStatus": "OK"
  }
]
```

An empty fleet returns an empty array.

## `GET /devices/{id}` — Get one device

Returns `200 OK` and a device object with the same fields as the list response.

| Status | Meaning |
| --- | --- |
| `200 OK` | Device found. |
| `404 Not Found` | Device ID is not registered. |

## `GET /summary` — Get fleet counts

Returns `200 OK` with calculated counts:

```json
{
  "total": 5,
  "online": 4,
  "offline": 1
}
```

For an empty fleet, all three values are zero.

## Response fields

| Field | Description |
| --- | --- |
| `id` | Unique, case-sensitive device ID. |
| `name` | Registered device name. |
| `status` | API-calculated connectivity: `ONLINE` or `OFFLINE`. |
| `lastHeartbeat` | Timestamp of the most recently stored heartbeat, or `null`. |
| `lastReportedStatus` | Status string from the most recently stored heartbeat, or `null`. |
| `total` | Number of registered devices. |
| `online` | Number of devices currently online. |
| `offline` | Number of devices currently offline. |

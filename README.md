# Device Fleet Monitor API

A small Spring Boot application for registering devices, receiving heartbeats, and monitoring current fleet connectivity through a REST API and a browser dashboard. Device records are stored in a local SQLite database. A PowerShell simulator can run five devices and lets you stop and restart an individual device.

## What it does

- Registers devices with a unique ID and name.
- Accepts a timestamped heartbeat and a device-reported status.
- Reports a device as `ONLINE` when its latest heartbeat is no more than 30 seconds old. Devices with no heartbeat or an older heartbeat are `OFFLINE`.
- Lists devices, returns one device's details, and summarizes online and offline counts.
- Serves a dashboard with fleet counts, device status, heartbeat age, device registration, search, and status filtering.
- Persists devices and their latest heartbeat in `fleet.db` in the project directory.

Connectivity (`status`) is calculated by the API. The device's submitted status is returned separately as `lastReportedStatus` (for example, `OK`). Send a current UTC timestamp; an old timestamp can make the device offline immediately, and future timestamps are rejected.

## Design

- `DeviceController` exposes the HTTP endpoints.
- `DeviceService` validates requests and applies the 30-second timeout rule.
- `DeviceRepository` uses Spring JDBC to access SQLite.
- `schema.sql` creates the device table when the application starts.
- `simulator/fleet-simulator.ps1` registers five devices and runs a heartbeat worker for each.

The API calculates status when a response is requested; it does not run a background task to mark records offline. SQLite stores the last heartbeat, and its age is evaluated on each API read.

## Prerequisites

- JDK 17 or newer.
- Windows PowerShell 5.1 or PowerShell 7 to use the included `mvnw.ps1` and simulator scripts.
- Internet access on the first build so the bootstrap script can download Maven and Maven can download dependencies.

You do not need to install Spring or Maven separately. Check Java with:

```powershell
java -version
```

## Build and run the API

Open PowerShell in the project directory (the directory containing `mvnw.ps1` and `pom.xml`):

```powershell
.\mvnw.ps1 clean package
.\mvnw.ps1 spring-boot:run
```

The API starts at `http://localhost:8080`; open that address in a browser for the dashboard. Keep this terminal open while using the dashboard or API. Stop the application with `Ctrl+C`.

## Dashboard

The dashboard refreshes the device list and fleet summary every five seconds. It displays each device's calculated connectivity, last reported status, and how long ago its most recent heartbeat was received. It provides browser views and controls for device registration, the fleet list, per-device information, and the summary. Per-device rows are populated from `GET /devices`; `GET /devices/{id}` is also available as a direct API call. The dashboard does not send heartbeats. A device or simulator should normally send them; for manual use, call `POST /devices/{id}/heartbeat` directly as shown below.

To run the packaged application after building:

```powershell
java -jar target/device-fleet-monitor-0.1.0.jar
```

## API

### Register a device

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/devices `
  -ContentType 'application/json' `
  -Body '{"id":"device-01","name":"Lab Device 01"}'
```

Registration returns `201 Created`. A new device has no heartbeat and is initially `OFFLINE`. A duplicate ID returns `409 Conflict`; blank required fields return `400 Bad Request`.

### Send a heartbeat

```powershell
$heartbeat = @{
  timestamp = [DateTime]::UtcNow.ToString('o')
  status = 'OK'
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri http://localhost:8080/devices/device-01/heartbeat `
  -ContentType 'application/json' -Body $heartbeat
```

The timestamp must be an ISO-8601 value. The heartbeat endpoint returns `404 Not Found` for an unregistered device and `400 Bad Request` when required heartbeat fields are missing or blank.

### Read device and fleet status

```powershell
# List all devices
Invoke-RestMethod http://localhost:8080/devices

# Get one device (404 if it is not registered)
Invoke-RestMethod http://localhost:8080/devices/device-01

# Get total, online, and offline counts
Invoke-RestMethod http://localhost:8080/summary
```

Device responses include `id`, `name`, calculated `status`, `lastHeartbeat`, and `lastReportedStatus`. The summary response includes `total`, `online`, and `offline`.

## Run the simulator

Start the API first. In a second PowerShell terminal, open the project directory and run:

```powershell
.\simulator\fleet-simulator.ps1
```

The script registers `device-01` through `device-05` (or reuses them if already registered) and sends heartbeats every five seconds by default. It checks that the API is reachable before starting workers. At the `simulator` prompt, use:

```text
stop device-03
status
start device-03
quit
```

After stopping a device, query `http://localhost:8080/devices/device-03` or `/summary`. It remains online until its last heartbeat is more than 30 seconds old, then reads as offline. `quit` stops and removes all simulator heartbeat workers; wait for the same timeout before checking that all devices have become offline. The simulator process does not change the API's stored heartbeat when it stops.

You can change the heartbeat interval (allowed range: 1–30 seconds) and API URL:

```powershell
.\simulator\fleet-simulator.ps1 -IntervalSeconds 3 -ApiUrl 'http://localhost:8080'
```

## Tests

Run the Java unit and API integration tests from the project directory:

```powershell
.\mvnw.ps1 test
```

They use a separate SQLite database under `target` and cover registration and persistence, duplicate and invalid requests, structured error responses, heartbeat updates, online/offline status, device lookup, list ordering, and fleet summary. The suite currently contains 13 Java tests.

The PowerShell simulator tests use Pester. If Pester is installed, run:

```powershell
Invoke-Pester .\simulator\Test-FleetSimulator.ps1
```

These check script syntax and startup checks, along with stopping/restarting a selected device and cleaning up simulator workers.

## Assumptions and known limitations

- Device IDs are unique and case-sensitive.
- Heartbeat timestamps use ISO-8601 and are provided by the device. The API rejects future timestamps.
- A device-reported status such as `OK` is informational; connectivity is determined only by heartbeat age.
- SQLite is intended for this single-instance local application. The database file is ignored by Git and created locally on startup.
- The application has no authentication or generated OpenAPI/Swagger page. The dashboard is served by the same Spring Boot process as the API.
- The simulator is a Windows PowerShell script and requires the API to be running.

## What I would improve with one additional day

I would add a dashboard control for sending a heartbeat with a selected device status. I would also make the SQLite path and heartbeat timeout configurable and add pagination for larger fleets.

## API documentation

See [API Reference](docs/API.md) for endpoint details, request and response formats, and HTTP status codes.

## AI Usage

- Tool: OpenAI Codex.
- Used to help scaffold the Spring Boot API, SQLite persistence, simulator, tests, and documentation.
- I personally modified the database structure to include the correct parameters as specified by the problem statement.
- I manually verified the SQLite-backed API and exercised the simulator's stop/quit behavior, including observing devices transition offline after the 30-second timeout.

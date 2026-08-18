# SpringTemporalDemo

A Spring Boot demo showing [Temporal](https://temporal.io/) workflow orchestration for a trip-booking system (flight + hotel + transport), including the **Saga pattern** for compensation on failure and a **signal-based user-confirmation** step.

## How it works

`POST /travel/book` starts a `TravelWorkflow` that books a flight, hotel, and transport in sequence (each retried up to 3 times). Each successful booking step registers a Saga compensation (cancel), so if a later step fails, the earlier ones are automatically rolled back in reverse order.

Once all three legs are booked, the workflow waits up to 2 minutes for a user confirmation signal (`POST /travel/confirm/{userId}`):
- If confirmed in time, the booking is confirmed.
- If not confirmed (or a step fails), the booking is cancelled and the already-booked legs are compensated via the Saga.

At any point you can check progress with `GET /travel/status/{userId}` (a Temporal Query), or change the travel date on an in-flight booking with `PUT /travel/date/{userId}` (a Temporal Update) — the update is rejected once the booking has been confirmed or cancelled.

The app is both a Spring Boot web server and a Temporal worker running in the same process — the worker registers the workflow/activities and starts polling on startup.

## Prerequisites

- Java 17
- Maven (`mvn` on your `PATH`; no wrapper is bundled)
- Docker + Docker Compose (to run a local Temporal server)

## Running locally

1. Start Temporal (server + Postgres + Web UI):
   ```
   docker-compose up -d
   ```
2. Run the app:
   ```
   mvn spring-boot:run
   ```
   The app starts on port `9191`.

Useful local endpoints:
- App / API: `http://localhost:9191`
- Swagger / OpenAPI UI: `http://localhost:9191/swagger-ui.html`
- Temporal Web UI: `http://localhost:8088`
- Temporal SDK/gRPC endpoint: `localhost:7233`

## API

| Method | Path | Description |
|---|---|---|
| `POST` | `/travel/book` | Starts a new trip-booking workflow for the given `TravelRequest` (`userId`, `destination`, `travelDate`) |
| `POST` | `/travel/confirm/{userId}` | Sends a confirmation signal to the running workflow for that user |
| `GET` | `/travel/status/{userId}` | Queries the current booking status (`BOOKING_FLIGHT`, `AWAITING_CONFIRMATION`, `CONFIRMED`, `CANCELLED`, etc.) |
| `PUT` | `/travel/date/{userId}` | Updates the travel date on an in-flight booking (body: new date string); rejected once confirmed or cancelled |

Workflow IDs are deterministic (`"travel_" + userId`), so only one active booking can be in flight per user at a time.

## Testing

```
mvn test
```

This runs the unit and Temporal-workflow tests (in-memory `TestWorkflowEnvironment`, no Docker required). One test, `SpringTemporalApplicationTests.contextLoads`, is tagged `integration` and excluded from the default run because it boots the full app and requires a live Temporal server. Run it explicitly once `docker-compose up -d` has been run:

```
mvn test -Dgroups=integration
```

## Tech stack

- Spring Boot 3.5.4 (Java 17)
- Temporal Java SDK 1.22.2, Temporal server `1.27.2` (via docker-compose)
- springdoc-openapi (Swagger UI)
- JUnit 5, Mockito, Temporal `temporal-testing`

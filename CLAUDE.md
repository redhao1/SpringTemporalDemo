# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

A Spring Boot demo showing Temporal workflow orchestration for a trip-booking system (flight + hotel + transport), including the Saga pattern for compensation on failure and a signal-based user-confirmation step.

## Commands

Build / run (Maven wrapper isn't present, use system `mvn`):
- Build: `mvn clean install`
- Run app: `mvn spring-boot:run` (starts on port `9191`)
- Run all tests (no Docker needed): `mvn test` — excludes tests tagged `integration` by default (see `maven-surefire-plugin` config in `pom.xml`)
- Run the Temporal integration smoke test (requires `docker-compose up -d` first): `mvn test -Dgroups=integration`
- Run a single test class: `mvn test -Dtest=SpringTemporalApplicationTests -DexcludedGroups=`

Temporal server dependency (must be running before starting the app, since `TemporalConfig` connects to Temporal on startup):
- Start: `docker-compose up -d`
- Temporal Web UI: http://localhost:8088
- Temporal SDK/gRPC endpoint: `localhost:7233`

Swagger/OpenAPI UI (springdoc is on the classpath): `http://localhost:9191/swagger-ui.html`

## Architecture

This app is both a Spring Boot web server (REST endpoints under `/travel`) and a Temporal worker, started in the same process:

- `TemporalConfig` (`config/`) builds `WorkflowServiceStubs`, creates a `WorkerFactory`/`Worker` bound to the `temporal.task-queue` value (`application.properties` → `TRAVEL_TASK_QUEUE`), registers `TravelWorkflowImpl` and `TravelActivities`, and starts the worker via `@PostConstruct`.
- `TravelWorkflowController` (`controller/`) exposes four endpoints:
  - `POST /travel/book` — starts a new workflow execution via `TravelBookingWorkflowStarter`.
  - `POST /travel/confirm/{userId}` — sends a signal to the running workflow instance for that user.
  - `GET /travel/status/{userId}` — queries the running (or recently-closed) workflow's current booking status.
  - `PUT /travel/date/{userId}` — sends an Update to change the travel date on an in-flight booking; body is the new date string.
- `TravelBookingWorkflowStarter` (`starter/`) creates workflow stubs (`WorkflowClient.newWorkflowStub`) to start workflows and deliver signals/queries/updates. Workflow IDs are deterministic: `"travel_" + userId`, which is how the starter re-attaches to an existing execution to send a signal, run a query, or send an update.
- `TravelWorkflow` / `TravelWorkflowImpl` (`workflow/`) define the workflow interface (`@WorkflowMethod bookTrip`, `@SignalMethod sendConfirmationSignal`, `@QueryMethod getBookingStatus`, `@UpdateMethod updateTravelDate` + its `@UpdateValidatorMethod`) and its implementation, which is the core orchestration logic:
  1. Books flight, hotel, and transport sequentially via activity stubs, each retryable (max 3 attempts, 10s start-to-close timeout). A `currentStatus` field is updated at each phase (`BOOKING_FLIGHT` → `BOOKING_HOTEL` → `ARRANGING_TRANSPORT` → `AWAITING_CONFIRMATION` → `CONFIRMED`/`CANCELLED`) and is what `getBookingStatus()` returns.
  2. Registers a Temporal `Saga` compensation for each successful booking step (cancel flight/hotel/transport) so a later failure unwinds prior steps in reverse.
  3. `Workflow.await(Duration, ...)` blocks up to 2 minutes for the `isUserConfirmed` flag to flip via the signal; on timeout, it rolls back the booked legs via `saga.compensate()` and then cancels the booking, otherwise it confirms it.
  4. Any exception during the booking steps also triggers `saga.compensate()`.
  5. `updateTravelDate(newDate)` mutates the in-memory `TravelRequest`'s travel date; its paired validator method rejects the update (throws `IllegalStateException`) once `currentStatus` is `CONFIRMED` or `CANCELLED`.
  6. Before returning, `bookTrip` calls `Workflow.sleep(Duration.ofSeconds(30))` — this is demo-only, simulating trailing wrap-up work so the workflow execution stays open long enough after reaching a terminal status to demonstrate the Update validator rejecting a live call. Without it, the workflow method returns (closing the execution) almost immediately after the terminal status is set.
- `TravelActivities` / `TravelActivitiesImpl` (`activities/`) are the individual booking/cancellation operations (currently simulated with logging, no real external calls). Note: `arrangeTransport` deliberately throws a `RuntimeException` to demonstrate the Saga compensation path — this is intentional demo behavior, not a bug.
- `TravelRequest` (`dto/`) is the plain data payload (`userId`, `destination`, `travelDate`) passed between the controller, starter, workflow, and activities.

### Key Temporal concepts to keep in mind when editing this code

- Workflow and activity implementations must remain deterministic where required by Temporal (e.g., no direct system calls, threading, or randomness inside workflow code — use `Workflow.*` helpers instead).
- The workflow ID scheme (`"travel_" + userId`) means only one active booking workflow can exist per user at a time; starting a second booking for the same user while one is in progress will hit the existing workflow ID.
- Activity retries and timeouts are configured once in `TravelWorkflowImpl.bookTrip` via `ActivityOptions`/`RetryOptions` — apply changes there rather than per-activity.

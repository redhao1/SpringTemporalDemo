package com.example.workflow;

import com.example.activities.TravelActivities;
import com.example.dto.TravelRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// Unit test of TravelWorkflowImpl

// TestWorkflowEnvironment is Temporal's in-memory test server: it auto-skips time past
// timers/awaits, so these run instantly and need no Docker/real Temporal server.
class TravelWorkflowImplTest {

    private static final String TASK_QUEUE = "test-travel-task-queue";

    private TestWorkflowEnvironment testEnv;
    private WorkflowClient client;
    private TravelActivities activities;

    @BeforeEach
    void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();

        Worker worker = testEnv.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(TravelWorkflowImpl.class);

        // Mock the activities
        activities = mock(TravelActivities.class);
        worker.registerActivitiesImplementations(activities);

        client = testEnv.getWorkflowClient();
        testEnv.start();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    private TravelWorkflow newWorkflowStub() {
        return client.newWorkflowStub(TravelWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).build());
    }

    @Test
    void compensatesFlightAndHotelWhenTransportArrangementFails() {
        TravelRequest request = new TravelRequest("user-1", "Paris", "2026-09-01");
        doThrow(new RuntimeException("Simulated transport arrangement failure!"))
                .when(activities).arrangeTransport(any());

        newWorkflowStub().bookTrip(request);

        verify(activities).bookFlight(request);
        verify(activities).bookHotel(request);
        // validate 3 attempts: TravelWorkflowImpl configures RetryOptions.setMaximumAttempts(3).
        verify(activities, times(3)).arrangeTransport(request);
        // Compensations run in reverse booking order.
        verify(activities).cancelHotel(request);
        verify(activities).cancelFlight(request);
        // cancelTransport's compensation is only registered after arrangeTransport succeeds,
        // which never happens here, so it must never be invoked.
        verify(activities, never()).cancelTransport(any());
        verify(activities, never()).confirmBooking(any());
        verify(activities, never()).cancelBooking(any());
    }

    @Test
    void confirmsBookingWhenUserSignalsWithinTimeout() {
        TravelRequest request = new TravelRequest("user-2", "Rome", "2026-10-01");

        // Start bookTrip asynchronously so the signal can be sent while it's still running;
        // the typed stub's signal method call sends the signal without blocking.
        TravelWorkflow workflow = newWorkflowStub();
        WorkflowClient.start(workflow::bookTrip, request);
        workflow.sendConfirmationSignal();

        // bookTrip is a void @WorkflowMethod, so waiting for completion goes through the
        // untyped stub with Void.class as the result type.
        WorkflowStub.fromTyped(workflow).getResult(Void.class);

        verify(activities).confirmBooking(request);
        verify(activities, never()).cancelBooking(any());
        verify(activities, never()).cancelFlight(any());
        verify(activities, never()).cancelHotel(any());
    }

    @Test
    void cancelsBookingWhenUserDoesNotConfirmWithinTimeout() {
        TravelRequest request = new TravelRequest("user-3", "Berlin", "2026-11-01");

        // No signal is sent, so this blocks on bookTrip's real 2-minute Workflow.await —
        // TestWorkflowEnvironment auto-skips that time, so the call still returns instantly.
        newWorkflowStub().bookTrip(request);

        verify(activities).cancelFlight(request);
        verify(activities).cancelHotel(request);
        verify(activities).cancelTransport(request);
        verify(activities).cancelBooking(request);
        verify(activities, never()).confirmBooking(any());
    }

    @Test
    void queryReturnsConfirmedStatusAfterUserConfirms() {
        TravelRequest request = new TravelRequest("user-4", "Madrid", "2026-12-01");

        TravelWorkflow workflow = newWorkflowStub();
        WorkflowClient.start(workflow::bookTrip, request);
        workflow.sendConfirmationSignal();
        WorkflowStub.fromTyped(workflow).getResult(Void.class);

        assertEquals("CONFIRMED", workflow.getBookingStatus());
    }
}

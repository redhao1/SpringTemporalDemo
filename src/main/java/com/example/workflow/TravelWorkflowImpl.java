package com.example.workflow;

import com.example.activities.TravelActivities;
import com.example.dto.TravelRequest;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Saga;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.Workflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
public class TravelWorkflowImpl implements TravelWorkflow {


    private boolean isUserConfirmed = false;

    private String currentStatus = "STARTED";

    private TravelRequest travelRequest;

    @SignalMethod
    public void sendConfirmationSignal() {
        log.info("📩 Received user confirmation signal.");
        isUserConfirmed = true;
    }

    @Override
    public String getBookingStatus() {
        return currentStatus;
    }

    @Override
    public void updateTravelDate(String newDate) {
        log.info("📝 Updating travel date for user: {} to {}", travelRequest.getUserId(), newDate);
        travelRequest.setTravelDate(newDate);
    }

    @Override
    public void validateUpdateTravelDate(String newDate) {
        if ("CONFIRMED".equals(currentStatus) || "CANCELLED".equals(currentStatus)) {
            throw new IllegalStateException(
                    "Cannot update travel date once the booking is " + currentStatus.toLowerCase());
        }
    }

    @Override
    public void bookTrip(TravelRequest travelRequest) {

        this.travelRequest = travelRequest;

        log.info("🚀 Starting travel booking for user: {}", travelRequest.getUserId());

        TravelActivities activities = Workflow.newActivityStub(TravelActivities.class,
                ActivityOptions.newBuilder()
                        .setRetryOptions(RetryOptions.newBuilder()
                                .setMaximumAttempts(3)
                                .build())
                        .setStartToCloseTimeout(Duration.ofSeconds(10))
                        .build());

        Saga.Options sagaOptions = new Saga.Options.Builder()
                .setParallelCompensation(false)
                .build();

        Saga saga = new Saga(sagaOptions);

        try {

            currentStatus = "BOOKING_FLIGHT";
            activities.bookFlight(travelRequest);
            saga.addCompensation(() -> activities.cancelFlight(travelRequest));

            currentStatus = "BOOKING_HOTEL";
            activities.bookHotel(travelRequest);
            saga.addCompensation(() -> activities.cancelHotel(travelRequest));

            currentStatus = "ARRANGING_TRANSPORT";
            activities.arrangeTransport(travelRequest);
            saga.addCompensation(() -> activities.cancelTransport(travelRequest));

            // 24 hours (1 day) -> wait for user confirmation if you won't
            // get any withing 24hr then cancel it

            log.info("⏳ Waiting for user confirmation for 2 min...");
            currentStatus = "AWAITING_CONFIRMATION";

            boolean isConfirmed = Workflow.await(
                    Duration.ofMinutes(2),
                    () -> isUserConfirmed
            );

            if (!isConfirmed) {
                log.info("🛑 User did not confirm within 2 minutes, cancelling the booking for user: {}", travelRequest.getUserId());
                //roll back the individually booked legs, then cancel the overall booking
                saga.compensate();
                activities.cancelBooking(travelRequest);
                currentStatus = "CANCELLED";
            } else {
                log.info("✅ User confirmed the booking: {}", travelRequest.getUserId());
                //confirm the booking
                activities.confirmBooking(travelRequest);
                currentStatus = "CONFIRMED";
            }


        } catch (Exception e) {
            log.error("❌ Error during travel booking for user: {}. Initiating compensation.", travelRequest.getUserId());
            saga.compensate();
            currentStatus = "CANCELLED";
        }

        // Simulates ongoing work so the workflow stays open for a bit after reaching a terminal
        // status
        log.info("🕒 Simulating ongoing booking process for user: {}...", travelRequest.getUserId());
        Workflow.sleep(Duration.ofSeconds(60));

        log.info("✅ Travel booking completed for user: {}", travelRequest.getUserId());

    }
}

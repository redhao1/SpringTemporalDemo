package com.example.activities;

import com.example.dto.TravelRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Unit tests of TravelActivitiesImpl
class TravelActivitiesImplTest {

    private final TravelActivitiesImpl activities = new TravelActivitiesImpl();
    private final TravelRequest request = new TravelRequest("user-1", "Paris", "2026-09-01");

    @Test
    void bookFlightCompletesWithoutError() {
        assertDoesNotThrow(() -> activities.bookFlight(request));
    }

    @Test
    void cancelFlightCompletesWithoutError() {
        assertDoesNotThrow(() -> activities.cancelFlight(request));
    }

    @Test
    void bookHotelCompletesWithoutError() {
        assertDoesNotThrow(() -> activities.bookHotel(request));
    }

    @Test
    void cancelHotelCompletesWithoutError() {
        assertDoesNotThrow(() -> activities.cancelHotel(request));
    }

    // Intentional demo behavior (see CLAUDE.md), not a bug: this activity always fails
    // so TravelWorkflowImplTest can exercise the Saga compensation path.
    @Test
    void arrangeTransportThrowsSimulatedFailure() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> activities.arrangeTransport(request));
        assertEquals("Simulated transport arrangement failure!", ex.getMessage());
    }

    @Test
    void cancelTransportCompletesWithoutError() {
        assertDoesNotThrow(() -> activities.cancelTransport(request));
    }

    @Test
    void cancelBookingCompletesWithoutError() {
        assertDoesNotThrow(() -> activities.cancelBooking(request));
    }

    @Test
    void confirmBookingCompletesWithoutError() {
        assertDoesNotThrow(() -> activities.confirmBooking(request));
    }
}

package com.example.controller;

import com.example.dto.TravelRequest;
import com.example.starter.TravelBookingWorkflowStarter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/travel")
public class TravelWorkflowController {

    private final TravelBookingWorkflowStarter starter;

    public TravelWorkflowController(TravelBookingWorkflowStarter starter) {
        this.starter = starter;
    }

    // Endpoint to start the travel booking workflow
    @PostMapping("/book")
    public ResponseEntity<String> bookTravel(@RequestBody TravelRequest travelRequest) {
        starter.startWorkFlow(travelRequest);
        return ResponseEntity.ok("Travel booking workflow started for user: " + travelRequest.getUserId());
    }

    // Endpoint to confirm the booking by sending a signal to the workflow
    @PostMapping("/confirm/{userId}")
    public ResponseEntity<String> confirmBooking(@PathVariable String userId) {
        starter.sendConfirmationSignal(userId);
        return ResponseEntity.ok("✅ Booking confirmed by user!");
    }

    // Endpoint to query the current status of the booking workflow
    @GetMapping("/status/{userId}")
    public ResponseEntity<String> getBookingStatus(@PathVariable String userId) {
        return ResponseEntity.ok(starter.getBookingStatus(userId));
    }

    // Endpoint to update the travel date of an in-flight booking workflow
    @PutMapping("/date/{userId}")
    public ResponseEntity<String> updateTravelDate(@PathVariable String userId, @RequestBody String newDate) {
        starter.updateTravelDate(userId, newDate);
        return ResponseEntity.ok("📝 Travel date updated for user: " + userId);
    }

}

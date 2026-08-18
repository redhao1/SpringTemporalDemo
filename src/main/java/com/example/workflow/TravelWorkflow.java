package com.example.workflow;

import com.example.dto.TravelRequest;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.UpdateMethod;
import io.temporal.workflow.UpdateValidatorMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface TravelWorkflow {

    @WorkflowMethod
    void bookTrip(TravelRequest travelRequest);


    @SignalMethod
    public void sendConfirmationSignal();

    @QueryMethod
    String getBookingStatus();

    @UpdateMethod
    void updateTravelDate(String newDate);

    @UpdateValidatorMethod(updateName = "updateTravelDate")
    void validateUpdateTravelDate(String newDate);

}

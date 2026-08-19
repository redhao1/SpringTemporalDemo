package com.example.starter;

import com.example.dto.TravelRequest;
import com.example.workflow.TravelWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TravelBookingWorkflowStarter {

    @Autowired
    private WorkflowClient workflowClient;

    @Value("${temporal.task-queue}")
    private String taskQueue;


    public void startWorkFlow(TravelRequest travelRequest){
        TravelWorkflow workflow = workflowClient.newWorkflowStub(
                TravelWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(taskQueue)
                        .setWorkflowId("travel_" + travelRequest.getUserId())
                        .build()
        );

        WorkflowClient.start(workflow::bookTrip, travelRequest);
    }


    public void sendConfirmationSignal(String userId) {
        String workflowId = "travel_" + userId;
        TravelWorkflow workflow = workflowClient.newWorkflowStub(TravelWorkflow.class, workflowId);

        workflow.sendConfirmationSignal();
    }

    public String getBookingStatus(String userId) {
        String workflowId = "travel_" + userId;
        TravelWorkflow workflow = workflowClient.newWorkflowStub(TravelWorkflow.class, workflowId);

        return workflow.getBookingStatus();
    }

    public void updateTravelDate(String userId, String newDate) {
        String workflowId = "travel_" + userId;
        TravelWorkflow workflow = workflowClient.newWorkflowStub(TravelWorkflow.class, workflowId);

        workflow.updateTravelDate(newDate);
    }
}

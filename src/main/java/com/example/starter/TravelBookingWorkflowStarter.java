package com.example.starter;

import com.example.dto.TravelRequest;
import com.example.workflow.TravelWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TravelBookingWorkflowStarter {

    @Autowired
    private WorkflowServiceStubs serviceStubs;

    @Value("${temporal.task-queue}")
    private String taskQueue;


    public void startWorkFlow(TravelRequest travelRequest){
        WorkflowClient client = WorkflowClient.newInstance(serviceStubs);

        TravelWorkflow workflow = client.newWorkflowStub(
                TravelWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(taskQueue)
                        .setWorkflowId("travel_" + travelRequest.getUserId())
                        .build()
        );

        WorkflowClient.start(workflow::bookTrip, travelRequest);
    }


    public void sendConfirmationSignal(String userId) {
        WorkflowClient client = WorkflowClient.newInstance(serviceStubs);

        String workflowId = "travel_" + userId;
        TravelWorkflow workflow = client.newWorkflowStub(TravelWorkflow.class, workflowId);

        workflow.sendConfirmationSignal();
    }

    public String getBookingStatus(String userId) {
        WorkflowClient client = WorkflowClient.newInstance(serviceStubs);

        String workflowId = "travel_" + userId;
        TravelWorkflow workflow = client.newWorkflowStub(TravelWorkflow.class, workflowId);

        return workflow.getBookingStatus();
    }

    public void updateTravelDate(String userId, String newDate) {
        WorkflowClient client = WorkflowClient.newInstance(serviceStubs);

        String workflowId = "travel_" + userId;
        TravelWorkflow workflow = client.newWorkflowStub(TravelWorkflow.class, workflowId);

        workflow.updateTravelDate(newDate);
    }
}

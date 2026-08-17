package com.example.config;

import com.example.activities.TravelActivities;
import com.example.workflow.TravelWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TemporalConfig {

    private final TravelActivities travelActivities;

    @Value("${temporal.task-queue}")
    private String taskQueue;

    public TemporalConfig(TravelActivities travelActivities) {
        this.travelActivities = travelActivities;
    }

    /**
     * Creates and configures a WorkerFactory for Temporal workflows.
     * Registers the TravelWorkflow and its activities to the specified task queue.
     *
     * @param serviceStubs Temporal service stubs for communication with the Temporal service
     * @return Configured WorkerFactory instance
     */
    @Bean
    public WorkerFactory workerFactory(WorkflowServiceStubs serviceStubs) {
        WorkflowClient client = WorkflowClient.newInstance(serviceStubs);
        WorkerFactory factory = WorkerFactory.newInstance(client);

        Worker worker = factory.newWorker(taskQueue);
        worker.registerWorkflowImplementationTypes(TravelWorkflowImpl.class);
        worker.registerActivitiesImplementations(travelActivities);

        return factory;
    }

    /**
     * Provides a WorkflowServiceStubs bean for connecting to the Temporal service.
     *
     * @return WorkflowServiceStubs instance
     */
    @Bean
    public WorkflowServiceStubs serviceStubs() {
        return WorkflowServiceStubs.newInstance();
    }

    /**
     * Starts the Temporal worker after the Spring context is initialized.
     */
    @PostConstruct
    public void startWorker() {
        workerFactory(serviceStubs()).start();
    }
}

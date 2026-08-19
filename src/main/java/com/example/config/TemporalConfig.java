package com.example.config;

import com.example.activities.TravelActivities;
import com.example.workflow.TravelWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
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
     * Provides a WorkflowServiceStubs bean for connecting to the Temporal service.
     *
     * @return WorkflowServiceStubs instance
     */
    @Bean
    public WorkflowServiceStubs serviceStubs() {
        return WorkflowServiceStubs.newInstance();
    }

    /**
     * Provides the WorkflowClient bean, shared by the worker and by anything that needs to
     * start/signal/query/update workflows (e.g. TravelBookingWorkflowStarter).
     */
    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs serviceStubs) {
        return WorkflowClient.newInstance(serviceStubs);
    }

    /**
     * Creates and configures a WorkerFactory for Temporal workflows.
     * Registers the TravelWorkflow and its activities to the specified task queue.
     *
     * @param workflowClient Temporal client the worker factory is built from
     * @return Configured WorkerFactory instance
     */
    @Bean
    public WorkerFactory workerFactory(WorkflowClient workflowClient) {
        WorkerFactory factory = WorkerFactory.newInstance(workflowClient);

        Worker worker = factory.newWorker(taskQueue);
        worker.registerWorkflowImplementationTypes(TravelWorkflowImpl.class);
        worker.registerActivitiesImplementations(travelActivities);

        return factory;
    }

    /**
     * Starts the worker factory once the Spring context is up, and shuts it down gracefully on
     * context close.
     */
    @Bean
    public SmartLifecycle workerFactoryLifecycle(WorkerFactory factory) {
        return new SmartLifecycle() {

            private volatile boolean running = false;

            @Override
            public void start() {
                factory.start();
                running = true;
            }

            @Override
            public void stop() {
                factory.shutdown();
                running = false;
            }

            @Override
            public boolean isRunning() {
                return running;
            }
        };
    }
}

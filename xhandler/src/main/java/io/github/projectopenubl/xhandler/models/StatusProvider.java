package io.github.projectopenubl.xhandler.models;

import java.time.Instant;
import java.util.List;

/** Platform status (DESIGN.md §15): workers, queues and SUNAT endpoints. Joins the caller's transaction. */
public interface StatusProvider {

    record WorkerCount(String role, long online) {}

    record QueueStats(int priorityClass, long waiting, long unblocked, long running, Instant oldestUnblocked) {}

    record EndpointState(String id, String breakerState, Instant breakerUntil, boolean paused) {}

    /** Workers with a heartbeat in the last 30 seconds, per role. */
    List<WorkerCount> getOnlineWorkers();

    /** Job queue depth per priority class. */
    List<QueueStats> getQueueStats();

    /** SUNAT's endpoints, ordered by id. */
    List<EndpointState> getSunatEndpoints();
}

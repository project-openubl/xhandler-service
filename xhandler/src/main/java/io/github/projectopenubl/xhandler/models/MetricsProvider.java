package io.github.projectopenubl.xhandler.models;

import java.util.Optional;

/** The cluster-wide health snapshot behind the SLO gauges (DESIGN.md §18). Joins the caller's transaction. */
public interface MetricsProvider {

    /** Documents that need a person, and documents waiting for SUNAT (nulls read as 0). */
    record DocumentHealth(double exception, double unknownStale, double pastDeadline, double pending) {}

    /** Ready jobs and the age of the oldest, in seconds (nulls read as 0). */
    record JobBacklog(double waiting, double oldestSeconds) {}

    /** A snapshot value computed in the last 5 minutes. */
    Optional<Double> getSnapshotValue(String name);

    DocumentHealth getDocumentHealth();

    JobBacklog getJobBacklog();

    /** Inserts or overwrites a snapshot value, stamped now. */
    void saveSnapshotValue(String name, double value);
}

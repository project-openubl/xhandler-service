package io.github.projectopenubl.xhandler.models;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Storage of {@link JobModel}s; every method joins the caller's transaction, which entered the tenant. */
public interface JobProvider {

    /** Creates a job with {@code id}: {@code init} sets its fields before it is persisted. */
    /** A new, not yet persisted job with {@code id}; see {@link #persist}. */
    JobModel newJob(UUID id);

    void persist(JobModel job);

    void delete(JobModel job);

    JobModel addJob(UUID id, Consumer<JobModel> init);

    Optional<JobModel> getJob(UUID id);

    /** Same as {@link #getJob}, locking the row until the transaction ends. */
    Optional<JobModel> getJobForUpdate(UUID id);

    /** Same as {@link #getJob}, share-locking the row until the transaction ends. */
    Optional<JobModel> getJobForShare(UUID id);

    /** Re-reads the job from the database (after native updates that bypass the session). */
    void refresh(JobModel job);

    /** Writes pending changes now, so constraint violations surface here. */
    void flush();

    /** Newest first; {@code state} and {@code name} are optional filters. */
    List<JobModel> getJobs(String state, String name, int offset, int limit);

    /** Asks a job to stop (one conditional update); returns its new state, or null. */
    String cancelTenantJob(UUID jobId);

    long countActiveJobs(String endpoint);
}

package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.models.utils.ModelToRepresentation;
import io.github.projectopenubl.xhandler.representations.idm.JobRepresentation;
import io.github.projectopenubl.xhandler.models.JobProvider;
import io.github.projectopenubl.xhandler.jobs.Jobs;
import io.github.projectopenubl.xhandler.models.JobModel;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.representations.idm.PageRepresentation;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;

/** A tenant's jobs, shaped like Pulp's tasks so xhandler-ui can reuse pulp-ui-2's patterns (§10.1). */
@Path("/api/v1/tenants/{tenantId}/jobs")
public class JobResource {

    private final ModelToRepresentation modelToRepresentation;

    private final JobProvider jobProvider;


    private final TenantAccess access;
    private final Jobs jobs;

    public JobResource(TenantAccess access, Jobs jobs, JobProvider jobProvider, ModelToRepresentation modelToRepresentation) {
        this.modelToRepresentation = modelToRepresentation;
        this.jobProvider = jobProvider;
        this.access = access;
        this.jobs = jobs;
    }

    @Operation(operationId = "listJobs")
    @GET
    @Transactional
    public PageRepresentation<JobRepresentation> list(
            @PathParam("tenantId") UUID tenantId,
            @QueryParam("state") String state,
            @QueryParam("name") String name,
            @QueryParam("limit") Integer limit,
            @QueryParam("offset") Integer offset) {
        access.enter(tenantId, Role.VIEWER);
        int size = PageRepresentation.limit(limit);
        int from = PageRepresentation.offset(offset);
        List<JobModel> page = jobProvider.getJobs(state, name, from, size);
        return new PageRepresentation<>(page.stream().map(modelToRepresentation::toRepresentation).toList(), size, from);
    }

    @Operation(operationId = "getJob")
    @GET
    @Path("/{jobId}")
    @Transactional
    public JobRepresentation get(@PathParam("tenantId") UUID tenantId, @PathParam("jobId") UUID jobId) {
        access.enter(tenantId, Role.VIEWER);
        JobModel job = jobProvider.getJob(jobId).orElse(null);
        if (job == null) {
            throw ApiException.notFound();
        }
        return modelToRepresentation.toRepresentation(job);
    }

    @Operation(operationId = "cancelJob")
    @POST
    @Path("/{jobId}/cancel")
    @Transactional
    public JobRepresentation cancel(@PathParam("tenantId") UUID tenantId, @PathParam("jobId") UUID jobId) {
        access.enter(tenantId, Role.OPERATOR);
        JobModel job = jobProvider.getJob(jobId).orElse(null);
        if (job == null) {
            throw ApiException.notFound();
        }
        if (!"waiting".equals(job.getState()) && !"running".equals(job.getState())) {
            throw ApiException.conflict("job_finished", "El trabajo ya terminó.");
        }
        if ("document.attempt".equals(job.getName())) {
            // SUNAT attempts are controlled through the document (hold/release), never canceled
            throw ApiException.conflict("job_not_cancelable", "Los envíos a SUNAT se controlan desde el documento.");
        }
        jobs.cancel(jobId);
        jobProvider.refresh(job);
        return modelToRepresentation.toRepresentation(job);
    }

}

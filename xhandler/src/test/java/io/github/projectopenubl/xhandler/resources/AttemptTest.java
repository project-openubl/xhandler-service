package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.models.DocumentEventProvider;
import io.github.projectopenubl.xhandler.models.JobModel;
import io.github.projectopenubl.xhandler.models.JobProvider;
import io.github.projectopenubl.xhandler.models.DocumentFileProvider;
import io.github.projectopenubl.xhandler.managers.DocumentControlManager;
import io.github.projectopenubl.xhandler.models.DocumentModel;

import static io.github.projectopenubl.xhandler.Api.tenantOf;
import static io.github.projectopenubl.xhandler.xml.EngineStub.Reply;
import static io.github.projectopenubl.xhandler.xml.EngineStub.sendAnswer;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.projectopenubl.xhandler.xml.EngineStub;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SUNAT attempts through the real job system (reconciler, unblocker, worker) against the scripted
 * engine: outcomes, the definitive-answer rule, ambiguity resolution and fencing (DESIGN.md §9.2).
 */
@QuarkusTest
@QuarkusTestResource(EngineStub.class)
class AttemptTest {

    @Inject
    DocumentEventProvider documentEventProvider;

    @Inject
    DocumentFileProvider documentFileProvider;

    @Inject
    JobProvider jobProvider;

    static final Duration WAIT = Duration.ofSeconds(20);

    @Inject
    TestDocuments docs;

    @Inject
    TestCompanies companies;

    @Inject
    TenantAccess access;

    UUID tenant;
    UUID company;
    String endpoint;

    @BeforeEach
    void setUp() {
        tenant = tenantOf("alice");
        company = companies.verified(tenant, "fake");
        endpoint = "t-" + UUID.randomUUID() + ":invoice";
    }

    @AfterEach
    void tearDown() {
        EngineStub.reset();
    }

    /** Answers {@code path} for this document with {@code reply}, and accepts anything else. */
    static Function<String, Reply> only(String marker, Supplier<Reply> reply) {
        return body -> body.contains(marker) ? reply.get() : Reply.ok(sendAnswer("accepted", 0, null, null));
    }

    DocumentModel awaitStatus(TestDocuments.Created doc, String status) {
        await().atMost(WAIT).until(() -> status.equals(docs.get(tenant, doc.id()).getStatus()));
        return docs.get(tenant, doc.id());
    }

    long sends(TestDocuments.Created doc) {
        return EngineStub.requests("/v1/send", doc.marker()).size();
    }

    @Test
    void accepted_document_stores_the_cdr_and_stops() {
        TestDocuments.Created doc = docs.pending(tenant, company, endpoint, null);

        DocumentModel accepted = awaitStatus(doc, "accepted");
        assertNull(accepted.getNextAttemptAt());
        assertNotNull(accepted.getCdrReceivedAt());
        assertNotNull(accepted.getFirstSentAt());
        assertEquals(1, accepted.getAttempts());
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenant);
            assertTrue(documentFileProvider.getDocumentFile(doc.id(), "cdr_zip").isPresent());
            assertTrue(documentEventProvider.getDocumentEvents(doc.id()).size() >= 1);
        });
    }

    @Test
    void nothing_is_sent_after_a_definitive_answer() throws Exception {
        EngineStub.on("/v1/send", only("__none__", () -> null));
        TestDocuments.Created doc = docs.pending(tenant, company, endpoint, null);
        EngineStub.on("/v1/send", only(doc.marker(), () -> Reply.ok(sendAnswer("rejected", 2335, null, null))));

        awaitStatus(doc, "rejected");
        // Even if it looks due again (a stale lease, a manual mistake), no attempt sends it
        docs.dueNow(tenant, doc.id());
        Thread.sleep(1500);
        assertEquals(1, sends(doc));
        assertEquals("rejected", docs.get(tenant, doc.id()).getStatus());
    }

    @Inject
    io.github.projectopenubl.xhandler.jobs.Jobs jobs;

    @Test
    void an_attempt_on_a_definitive_document_does_nothing() {
        TestDocuments.Created doc = docs.pending(tenant, company, endpoint, d -> {
            d.setStatus("accepted");
            d.setNextAttemptAt(null);
        });
        // An attempt job that got through anyway (stale lease, operator mistake)
        UUID job = QuarkusTransaction.requiringNew().call(() -> jobs.enqueue(access.enterAsPlatform(tenant), "document.attempt", 2,
                java.util.Map.of("document_id", doc.id().toString()), java.util.List.of("document:" + doc.id()), null).getId());
        await().atMost(WAIT).until(() -> QuarkusTransaction.requiringNew().call(() -> {
            access.enterAsPlatform(tenant);
            JobModel j = jobProvider.getJob(job).orElse(null);
            return "completed".equals(j.getState()) && "definitive".equals(j.getResult().get("skipped"));
        }));
        assertEquals(0, sends(doc));
    }

    @Test
    void transient_sunat_errors_are_retried_with_backoff() {
        EngineStub.on("/v1/send", body -> Reply.ok(sendAnswer("accepted", 0, null, null)));
        TestDocuments.Created doc = docs.pending(tenant, company, endpoint, null);
        EngineStub.on("/v1/send", only(doc.marker(), () -> Reply.ok(sendAnswer("exception", 109, "transient_system", null))));

        await().atMost(WAIT).until(() -> sends(doc) >= 1 && docs.get(tenant, doc.id()).getLastOutcome() != null);
        DocumentModel waiting = docs.get(tenant, doc.id());
        assertEquals("pending_send", waiting.getStatus());
        // First backoff step: 1 minute ±20%
        assertTrue(waiting.getNextAttemptAt().isAfter(Instant.now().plusSeconds(40)), "next attempt " + waiting.getNextAttemptAt());

        EngineStub.on("/v1/send", only(doc.marker(), () -> Reply.ok(sendAnswer("accepted", 0, null, null))));
        docs.dueNow(tenant, doc.id());
        awaitStatus(doc, "accepted");
        assertEquals(2, sends(doc));
    }

    @Test
    void permanent_errors_stop_until_a_manual_retry_with_the_same_bytes() {
        TestDocuments.Created doc = docs.pending(tenant, company, endpoint, d -> d.setNextAttemptAt(null));
        EngineStub.on("/v1/send", only(doc.marker(), () -> Reply.ok(sendAnswer("exception", 1034, "permanent_content", null))));
        docs.dueNow(tenant, doc.id());

        DocumentModel failed = awaitStatus(doc, "exception");
        assertNull(failed.getNextAttemptAt());
        assertEquals("permanent_content", failed.getCodeClass());

        EngineStub.on("/v1/send", only(doc.marker(), () -> Reply.ok(sendAnswer("accepted", 0, null, null))));
        QuarkusTransaction.requiringNew().run(() -> {
            var scope = access.enterAsPlatform(tenant);
            control.retry(scope, doc.id());
        });
        awaitStatus(doc, "accepted");
        assertEquals(2, sends(doc));
        // The same signed bytes both times
        var bodies = EngineStub.requests("/v1/send", doc.marker());
        assertEquals(bodies.get(0).body(), bodies.get(1).body());
    }

    @Inject
    DocumentControlManager control;

    @Test
    void an_ambiguous_send_is_resolved_before_anything_else() {
        TestDocuments.Created doc = docs.pending(tenant, company, endpoint, d -> d.setNextAttemptAt(null));
        // The answer is lost after SUNAT may have received the document
        EngineStub.on("/v1/send", only(doc.marker(), () -> Reply.ok(sendAnswer("unknown", null, null, null))));
        docs.dueNow(tenant, doc.id());
        awaitStatus(doc, "unknown");

        // Resolution asks SUNAT's consult service first (the fake has it): SUNAT has it, accepted
        EngineStub.on("/v1/status-cdr", body -> body.contains("\"numero\":" + docs.get(tenant, doc.id()).getNumero())
                ? Reply.ok("{\"status\":\"accepted\",\"code\":\"0001\",\"description\":\"aceptado\",\"cdr_zip\":\"UEsDBA==\"}")
                : Reply.ok("{\"status\":\"not_found\",\"code\":\"0011\",\"description\":\"no existe\",\"cdr_zip\":null}"));
        docs.dueNow(tenant, doc.id());
        DocumentModel accepted = awaitStatus(doc, "accepted");
        assertEquals(1, sends(doc), "resolved without resending");
        assertNotNull(accepted.getCdrReceivedAt());
    }

    @Test
    void sunat_registered_with_the_same_bytes_is_accepted_and_with_other_bytes_is_a_collision() {
        TestDocuments.Created same = docs.pending(tenant, company, endpoint, d -> d.setNextAttemptAt(null));
        TestDocuments.Created other = docs.pending(tenant, company, endpoint, d -> d.setNextAttemptAt(null));
        EngineStub.on("/v1/send", body -> body.contains(same.marker()) || body.contains(other.marker())
                ? Reply.ok(sendAnswer("exception", 1033, "already_registered", null))
                : Reply.ok(sendAnswer("accepted", 0, null, null)));
        EngineStub.on("/v1/validity/file", body -> body.contains(same.marker())
                ? Reply.ok("{\"state\":\"matches\",\"code\":\"0001\",\"description\":\"ok\"}")
                : Reply.ok("{\"state\":\"differs\",\"code\":\"0002\",\"description\":\"otros datos\"}"));
        docs.dueNow(tenant, same.id());
        docs.dueNow(tenant, other.id());

        awaitStatus(same, "accepted");
        DocumentModel collided = awaitStatus(other, "exception");
        assertTrue(collided.getSunatDescription().startsWith("Colisión de numeración"), collided.getSunatDescription());
    }

    @Test
    void tickets_are_checked_until_sunat_answers() {
        TestDocuments.Created doc = docs.pending(tenant, company, endpoint, d -> d.setNextAttemptAt(null));
        EngineStub.on("/v1/send", only(doc.marker(), () -> Reply.ok(sendAnswer("in_progress", null, null, "1712345678901"))));
        EngineStub.on("/v1/verify-ticket", body -> Reply.ok(sendAnswer("accepted", 0, null, null)));
        docs.dueNow(tenant, doc.id());
        DocumentModel checking = awaitStatus(doc, "in_progress");
        assertEquals("1712345678901", checking.getTicket());

        docs.dueNow(tenant, doc.id());
        awaitStatus(doc, "accepted");
        assertEquals(1, EngineStub.requests("/v1/verify-ticket", "1712345678901").size());
        assertEquals(1, sends(doc));
    }

    @Test
    void a_stale_attempt_writes_nothing() throws Exception {
        TestDocuments.Created doc = docs.pending(tenant, company, endpoint, d -> d.setNextAttemptAt(null));
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        EngineStub.on("/v1/send", only(doc.marker(), () -> {
            inside.countDown();
            try {
                release.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Reply.ok(sendAnswer("rejected", 2335, null, null));
        }));
        docs.dueNow(tenant, doc.id());
        assertTrue(inside.await(20, TimeUnit.SECONDS));

        // Meanwhile a newer attempt started (e.g. after recovery): attempts moved on
        docs.update(tenant, doc.id(), d -> d.setAttempts(d.getAttempts() + 1));
        release.countDown();

        await().atMost(WAIT).until(() -> EngineStub.requests("/v1/send", doc.marker()).size() == 1);
        Thread.sleep(1000);
        DocumentModel after = docs.get(tenant, doc.id());
        assertEquals("pending_send", after.getStatus(), "the stale attempt's rejection was not written");
    }

    @Test
    void identical_failures_flag_needs_attention_but_keep_retrying() {
        TestDocuments.Created doc = docs.pending(tenant, company, endpoint, d -> {
            d.setNextAttemptAt(null);
            d.setLastOutcome("NOT_DELIVERED:-");
            d.setSameOutcomeCount(9);
        });
        EngineStub.on("/v1/send", only(doc.marker(), () -> Reply.ok(sendAnswer("not_delivered", null, null, null))));
        docs.dueNow(tenant, doc.id());

        await().atMost(WAIT).until(() -> "needs_attention".equals(docs.get(tenant, doc.id()).getHoldReason()));
        DocumentModel flagged = docs.get(tenant, doc.id());
        assertEquals("pending_send", flagged.getStatus());
        assertTrue(flagged.getNextAttemptAt().isAfter(Instant.now().plus(Duration.ofHours(5))), "retried every 6 hours");
    }

    @Test
    void held_documents_are_not_attempted_until_released() throws Exception {
        TestDocuments.Created doc = docs.pending(tenant, company, endpoint, d -> {
            d.setHoldReason("operator");
        });
        Thread.sleep(1500);
        assertEquals(0, sends(doc));

        QuarkusTransaction.requiringNew().run(() -> control.release(access.enterAsPlatform(tenant), doc.id()));
        awaitStatus(doc, "accepted");
    }
}

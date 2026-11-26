package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.OseProvider;
import io.github.projectopenubl.xhandler.utils.Times;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaOseProvider implements OseProvider {

    private final EntityManager em;

    public JpaOseProvider(EntityManager em) {
        this.em = em;
    }

    @SuppressWarnings("unchecked")
    private static List<Object[]> rows(jakarta.persistence.Query query) {
        return query.getResultList();
    }

    @Override
    public void addClient(UUID id, String issuerRuc, String username, String passwordHash, UUID tenantId, String createdBy) {
        em.createNativeQuery("""
                        INSERT INTO xhandler.ose_client (id, issuer_ruc, username, password_hash, tenant_id, created_by)
                        VALUES (:id, :ruc, :u, :h, :t, :by)""")
                .setParameter("id", id).setParameter("ruc", issuerRuc).setParameter("u", username)
                .setParameter("h", passwordHash).setParameter("t", tenantId).setParameter("by", createdBy).executeUpdate();
        em.flush();
    }

    @Override
    public List<ClientView> getClients() {
        return rows(em.createNativeQuery("""
                        SELECT id, issuer_ruc, username, tenant_id, status, locked_until, last_login_at FROM xhandler.ose_client ORDER BY created_at"""))
                .stream().map(r -> new ClientView((UUID) r[0], (String) r[1], (String) r[2], (UUID) r[3], (String) r[4], Times.toInstant(r[5]),
                        Times.toInstant(r[6]))).toList();
    }

    @Override
    public Optional<ClientLogin> getClientLogin(String username) {
        return rows(em.createNativeQuery("""
                        SELECT id, issuer_ruc, username, tenant_id, password_hash, status FROM xhandler.ose_client WHERE username = :u""")
                .setParameter("u", username)).stream().findFirst()
                .map(r -> new ClientLogin((UUID) r[0], (String) r[1], (String) r[2], (UUID) r[3], (String) r[4], (String) r[5]));
    }

    @Override
    public Optional<Client> getActiveClient(String username) {
        return rows(em.createNativeQuery(
                        "SELECT id, issuer_ruc, username, tenant_id FROM xhandler.ose_client WHERE username = :u AND status = 'active'")
                .setParameter("u", username)).stream().findFirst()
                .map(r -> new Client((UUID) r[0], (String) r[1], (String) r[2], (UUID) r[3]));
    }

    @Override
    public int countFailedLogin(UUID id) {
        return ((Number) em.createNativeQuery(
                        "UPDATE xhandler.ose_client SET failed_attempts = failed_attempts + 1 WHERE id = :id RETURNING failed_attempts")
                .setParameter("id", id).getSingleResult()).intValue();
    }

    @Override
    public void markLogin(UUID id) {
        em.createNativeQuery("""
                        UPDATE xhandler.ose_client SET failed_attempts = 0, last_login_at = now()
                        WHERE id = :id AND (failed_attempts > 0 OR last_login_at IS NULL OR last_login_at < now() - interval '1 minute')""")
                .setParameter("id", id).executeUpdate();
    }

    @Override
    public int suspendClient(UUID id) {
        return em.createNativeQuery("UPDATE xhandler.ose_client SET status = 'suspended' WHERE id = :id").setParameter("id", id)
                .executeUpdate();
    }

    @Override
    public int activateClient(UUID id) {
        return em.createNativeQuery(
                        "UPDATE xhandler.ose_client SET status = 'active', failed_attempts = 0, locked_until = NULL WHERE id = :id")
                .setParameter("id", id).executeUpdate();
    }

    @Override
    public int resetClientPassword(UUID id, String passwordHash) {
        return em.createNativeQuery(
                        "UPDATE xhandler.ose_client SET password_hash = :h, failed_attempts = 0, locked_until = NULL WHERE id = :id")
                .setParameter("h", passwordHash).setParameter("id", id).executeUpdate();
    }

    @Override
    public void deleteAssociations() {
        em.createNativeQuery("DELETE FROM xhandler.ose_association").executeUpdate();
    }

    @Override
    public void addAssociation(String ruc, LocalDate startsOn, LocalDate endsOn) {
        em.createNativeQuery("""
                        INSERT INTO xhandler.ose_association (ruc, starts_on, ends_on) VALUES (:r, :s, :e)
                        ON CONFLICT (ruc) DO UPDATE SET starts_on = excluded.starts_on, ends_on = excluded.ends_on, imported_at = now()""")
                .setParameter("r", ruc).setParameter("s", startsOn).setParameter("e", endsOn).executeUpdate();
    }

    @Override
    public boolean isAssociated(String ruc, LocalDate today) {
        return !em.createNativeQuery("""
                        SELECT 1 FROM xhandler.ose_association WHERE ruc = :r AND starts_on <= :today
                          AND (ends_on IS NULL OR :today <= (date_trunc('month', ends_on) + interval '1 month' + interval '6 days')::date)""")
                .setParameter("r", ruc).setParameter("today", today).getResultList().isEmpty();
    }

    @Override
    public void deleteBlockedList(String list) {
        em.createNativeQuery("DELETE FROM xhandler.blocked_issuer WHERE list = :l").setParameter("l", list).executeUpdate();
    }

    @Override
    public void addBlockedIssuer(String ruc, String list, LocalDate listedOn) {
        em.createNativeQuery("""
                        INSERT INTO xhandler.blocked_issuer (ruc, list, listed_on) VALUES (:r, :l, :d) ON CONFLICT (ruc, list) DO NOTHING""")
                .setParameter("r", ruc).setParameter("l", list).setParameter("d", listedOn).executeUpdate();
    }

    @Override
    public boolean isBlocked(String ruc) {
        return !em.createNativeQuery("SELECT 1 FROM xhandler.blocked_issuer WHERE ruc = :r").setParameter("r", ruc).getResultList().isEmpty();
    }

    @Override
    public Optional<AcceptedSubmission> getAcceptedSubmission(String issuerRuc, String tipo, String serie, long numero) {
        return rows(em.createNativeQuery("""
                        SELECT sha256, cdr_ref, id FROM xhandler.ose_submission
                        WHERE issuer_ruc = :r AND tipo = :t AND serie = :s AND numero = :n AND result = 'cdr'""")
                .setParameter("r", issuerRuc).setParameter("t", tipo).setParameter("s", serie).setParameter("n", numero))
                .stream().findFirst().map(r -> new AcceptedSubmission((String) r[0], (String) r[1], (UUID) r[2]));
    }

    @Override
    public void addSubmission(NewSubmission s) {
        em.createNativeQuery("""
                        INSERT INTO xhandler.ose_submission (id, ose_client_id, issuer_ruc, tipo, serie, numero, name, channel, ticket,
                            received_at, result, response_code, description, sha256, xml_ref, cdr_ref, cdr_issued_at, forward_status,
                            next_attempt_at, purge_after)
                        VALUES (:id, :client, :ruc, :tipo, :serie, :numero, :name, :channel, :ticket, :received, :result, :code, :description,
                            :sha, :xml, :cdr, :issued, :forward, :next, :purge)""")
                .setParameter("id", s.id()).setParameter("client", s.clientId()).setParameter("ruc", s.issuerRuc())
                .setParameter("tipo", s.tipo()).setParameter("serie", s.serie()).setParameter("numero", s.numero())
                .setParameter("name", s.name()).setParameter("channel", s.channel()).setParameter("ticket", s.ticket())
                .setParameter("received", s.receivedAt()).setParameter("result", s.result()).setParameter("code", s.responseCode())
                .setParameter("description", s.description()).setParameter("sha", s.sha256())
                .setParameter("xml", s.xmlRef()).setParameter("cdr", s.cdrRef())
                .setParameter("issued", s.cdrIssuedAt()).setParameter("forward", s.forwardStatus())
                .setParameter("next", s.nextAttemptAt()).setParameter("purge", s.purgeAfter())
                .executeUpdate();
    }

    @Override
    public void addTicketItem(String ticket, UUID clientId, UUID submissionId) {
        em.createNativeQuery("INSERT INTO xhandler.ose_ticket_item (ticket, ose_client_id, submission_id) VALUES (:t, :c, :s)")
                .setParameter("t", ticket).setParameter("c", clientId).setParameter("s", submissionId).executeUpdate();
    }

    @Override
    public void linkTicketItem(String ticket, UUID clientId, UUID submissionId) {
        em.createNativeQuery("""
                        INSERT INTO xhandler.ose_ticket_item (ticket, ose_client_id, submission_id) VALUES (:t, :c, :s) ON CONFLICT DO NOTHING""")
                .setParameter("t", ticket).setParameter("c", clientId).setParameter("s", submissionId).executeUpdate();
    }

    @Override
    public void countValidation(UUID clientId, LocalDate day) {
        em.createNativeQuery("""
                        INSERT INTO xhandler.ose_usage (ose_client_id, day, validations) VALUES (:c, :d, 1)
                        ON CONFLICT (ose_client_id, day) DO UPDATE SET validations = ose_usage.validations + 1""")
                .setParameter("c", clientId).setParameter("d", day).executeUpdate();
    }

    @Override
    public List<TicketItem> getTicketItems(String ticket, UUID clientId) {
        return rows(em.createNativeQuery("""
                        SELECT s.name, s.result, s.response_code, s.description, s.cdr_ref
                        FROM xhandler.ose_ticket_item i JOIN xhandler.ose_submission s ON s.id = i.submission_id
                        WHERE i.ticket = :t AND i.ose_client_id = :c ORDER BY s.name""").setParameter("t", ticket).setParameter("c", clientId))
                .stream().map(r -> new TicketItem((String) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4])).toList();
    }

    @Override
    public Optional<SubmissionCdr> getSubmissionCdr(String issuerRuc, String tipo, String serie, long numero) {
        return rows(em.createNativeQuery("""
                        SELECT name, cdr_ref FROM xhandler.ose_submission
                        WHERE issuer_ruc = :r AND tipo = :t AND serie = :s AND numero = :n AND result = 'cdr' AND cdr_ref IS NOT NULL""")
                .setParameter("r", issuerRuc).setParameter("t", tipo).setParameter("s", serie).setParameter("n", numero))
                .stream().findFirst().map(r -> new SubmissionCdr((String) r[0], (String) r[1]));
    }

    @Override
    public List<Usage> getUsage(LocalDate from, LocalDate to) {
        return rows(em.createNativeQuery("""
                        SELECT c.id, c.issuer_ruc, c.username, c.tenant_id, coalesce(sum(u.validations), 0)
                        FROM xhandler.ose_client c LEFT JOIN xhandler.ose_usage u ON u.ose_client_id = c.id AND u.day BETWEEN :from AND :to
                        GROUP BY c.id ORDER BY c.issuer_ruc""")
                .setParameter("from", from).setParameter("to", to))
                .stream().map(x -> new Usage((UUID) x[0], (String) x[1], (String) x[2], (UUID) x[3], ((Number) x[4]).longValue())).toList();
    }

    @Override
    public ForwardingStats getForwardingStats() {
        Object[] r = (Object[]) em.createNativeQuery("""
                        SELECT count(*) FILTER (WHERE forward_status IN ('pending', 'blocked')),
                               extract(epoch FROM now() - min(cdr_issued_at) FILTER (WHERE forward_status = 'pending')),
                               count(*) FILTER (WHERE forward_status = 'rejected'),
                               count(*) FILTER (WHERE forward_status = 'forwarded' AND forwarded_at > cdr_issued_at + interval '1 hour'),
                               count(*) FILTER (WHERE forward_status = 'forwarded')
                        FROM xhandler.ose_submission""").getSingleResult();
        return new ForwardingStats(((Number) r[0]).longValue(), r[1] == null ? 0 : ((Number) r[1]).doubleValue(), ((Number) r[2]).longValue(),
                ((Number) r[3]).longValue(), ((Number) r[4]).longValue());
    }

    @Override
    public List<RejectedForward> getRejectedForwards() {
        return rows(em.createNativeQuery("""
                        SELECT id, name, last_error, cdr_issued_at FROM xhandler.ose_submission WHERE forward_status = 'rejected'
                        ORDER BY cdr_issued_at DESC LIMIT 50"""))
                .stream().map(x -> new RejectedForward((UUID) x[0], (String) x[1], (String) x[2], Times.toInstant(x[3]))).toList();
    }

    @Override
    public long countBlockedForwards() {
        return ((Number) em.createNativeQuery("SELECT count(*) FROM xhandler.ose_submission WHERE forward_status = 'blocked'")
                .getSingleResult()).longValue();
    }

    @Override
    public double getOldestForwardSeconds() {
        List<?> age = em.createNativeQuery("""
                        SELECT extract(epoch FROM now() - min(cdr_issued_at)) FROM xhandler.ose_submission
                        WHERE forward_status IN ('pending', 'blocked')""").getResultList();
        return age.isEmpty() || age.get(0) == null ? 0.0 : ((Number) age.get(0)).doubleValue();
    }

    @Override
    public int markReforward(UUID id) {
        return em.createNativeQuery("""
                        UPDATE xhandler.ose_submission SET forward_status = 'pending', next_attempt_at = now()
                        WHERE id = :id AND result = 'cdr' AND forward_status = 'rejected'""").setParameter("id", id).executeUpdate();
    }

    @Override
    public int unblockDueForwards() {
        return em.createNativeQuery("""
                        UPDATE xhandler.ose_submission SET forward_status = 'pending'
                        WHERE forward_status = 'blocked' AND next_attempt_at <= now()""").executeUpdate();
    }

    @Override
    public List<DueForward> leaseForwards(UUID lease) {
        return rows(em.createNativeQuery("""
                        WITH due AS (
                            SELECT id FROM xhandler.ose_submission
                            WHERE forward_status = 'pending' AND next_attempt_at <= now()
                            ORDER BY cdr_issued_at LIMIT 50 FOR UPDATE SKIP LOCKED)
                        UPDATE xhandler.ose_submission s SET next_attempt_at = now() + interval '2 minutes', forward_lease = :lease
                        FROM due WHERE s.id = due.id
                        RETURNING s.id, s.name, s.tipo, s.xml_ref, s.cdr_ref, s.cdr_issued_at, s.forward_attempts""")
                .setParameter("lease", lease))
                .stream().map(r -> new DueForward((UUID) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4],
                        Times.toInstant(r[5]), ((Number) r[6]).intValue())).toList();
    }

    @Override
    public int releaseForward(UUID id, UUID lease) {
        return em.createNativeQuery("""
                        UPDATE xhandler.ose_submission SET next_attempt_at = now(), forward_lease = NULL
                        WHERE id = :id AND forward_status = 'pending' AND forward_lease = :lease""")
                .setParameter("id", id).setParameter("lease", lease).executeUpdate();
    }

    @Override
    public int retryForward(UUID id, UUID lease, String error, String wait) {
        return em.createNativeQuery("""
                        UPDATE xhandler.ose_submission SET forward_attempts = forward_attempts + 1, last_error = :e,
                            next_attempt_at = now() + cast(:wait AS interval)
                        WHERE id = :id AND forward_status = 'pending' AND forward_lease = :lease""")
                .setParameter("e", error).setParameter("wait", wait).setParameter("id", id).setParameter("lease", lease).executeUpdate();
    }

    @Override
    public int markForwarded(UUID id, UUID lease, String ack) {
        return em.createNativeQuery("""
                        UPDATE xhandler.ose_submission SET forward_status = 'forwarded', forwarded_at = now(), sunat_ack = :ack,
                            forward_attempts = forward_attempts + 1, next_attempt_at = NULL, last_error = NULL
                        WHERE id = :id AND forward_status = 'pending' AND forward_lease = :lease""")
                .setParameter("ack", ack).setParameter("id", id).setParameter("lease", lease).executeUpdate();
    }

    @Override
    public int markForwardRejected(UUID id, UUID lease, String error) {
        return em.createNativeQuery("""
                        UPDATE xhandler.ose_submission SET forward_status = 'rejected', forward_attempts = forward_attempts + 1,
                            next_attempt_at = NULL, last_error = :e
                        WHERE id = :id AND forward_status = 'pending' AND forward_lease = :lease""")
                .setParameter("e", error).setParameter("id", id).setParameter("lease", lease).executeUpdate();
    }

    @Override
    public int markForwardBlocked(UUID id, UUID lease, String error) {
        return em.createNativeQuery("""
                        UPDATE xhandler.ose_submission SET forward_status = 'blocked', forward_attempts = forward_attempts + 1,
                            next_attempt_at = now() + interval '10 minutes', last_error = :e
                        WHERE id = :id AND forward_status = 'pending' AND forward_lease = :lease""")
                .setParameter("e", error).setParameter("id", id).setParameter("lease", lease).executeUpdate();
    }

    @Override
    public List<PurgedFiles> deletePurgeableSubmissions() {
        return rows(em.createNativeQuery("""
                        DELETE FROM xhandler.ose_submission
                        WHERE purge_after < now() AND (result = 'inconsistency' OR forward_status = 'forwarded')
                        RETURNING xml_ref, cdr_ref"""))
                .stream().map(r -> new PurgedFiles((String) r[0], (String) r[1])).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getReferencedFiles(List<String> refs) {
        return em.createNativeQuery("""
                        SELECT ref FROM unnest(cast(:refs AS text[])) ref
                        WHERE EXISTS (SELECT 1 FROM xhandler.ose_submission s WHERE s.xml_ref = ref OR s.cdr_ref = ref)""")
                .setParameter("refs", refs.toArray(String[]::new)).getResultList();
    }
}

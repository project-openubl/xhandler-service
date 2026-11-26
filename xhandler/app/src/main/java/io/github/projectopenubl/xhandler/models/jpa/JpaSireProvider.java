package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.models.SireProvider;
import io.github.projectopenubl.xhandler.utils.Times;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaSireProvider implements SireProvider {

    private final EntityManager em;

    public JpaSireProvider(EntityManager em) {
        this.em = em;
    }

    @SuppressWarnings("unchecked")
    private static List<Object[]> rows(jakarta.persistence.Query query) {
        return query.getResultList();
    }

    @Override
    public int addPeriod(UUID id, UUID tenantId, UUID companyId, String sunatEnv, String book, String period, String requestedBy) {
        return em.createNativeQuery("""
                        INSERT INTO xhandler.sire_period (id, tenant_id, company_id, sunat_env, book, period, status, requested_by)
                        VALUES (:id, :t, :c, :env, :b, :p, 'requested', :by) ON CONFLICT (company_id, sunat_env, book, period) DO NOTHING""")
                .setParameter("id", id).setParameter("t", tenantId).setParameter("c", companyId).setParameter("env", sunatEnv)
                .setParameter("b", book).setParameter("p", period).setParameter("by", requestedBy).executeUpdate();
    }

    @Override
    public PeriodState getPeriodStateForUpdate(UUID companyId, String sunatEnv, String book, String period) {
        Object[] r = (Object[]) em.createNativeQuery("""
                        SELECT id, status FROM xhandler.sire_period WHERE company_id = :c AND sunat_env = :env AND book = :b AND period = :p FOR UPDATE""")
                .setParameter("c", companyId).setParameter("env", sunatEnv).setParameter("b", book).setParameter("p", period)
                .getSingleResult();
        return new PeriodState((UUID) r[0], (String) r[1]);
    }

    @Override
    public int restartPeriod(UUID id, String requestedBy) {
        return em.createNativeQuery("""
                        UPDATE xhandler.sire_period SET status = 'requested', proposal_ticket = NULL, proposal_ref = NULL, reconciliation = NULL,
                            detail = NULL, resume_status = NULL, poll_attempts = 0, next_poll_at = now(), requested_by = :by, updated_at = now()
                        WHERE id = :id""")
                .setParameter("by", requestedBy).setParameter("id", id).executeUpdate();
    }

    @Override
    public Optional<UUID> getPeriodId(UUID companyId, String sunatEnv, String book, String period) {
        List<?> found = em.createNativeQuery("SELECT id FROM xhandler.sire_period WHERE company_id = :c AND sunat_env = :env AND book = :b AND period = :p")
                .setParameter("c", companyId).setParameter("env", sunatEnv).setParameter("b", book).setParameter("p", period).getResultList();
        return found.isEmpty() ? Optional.empty() : Optional.of((UUID) found.get(0));
    }

    @Override
    public ReplaceState getReplaceStateForUpdate(UUID id) {
        Object[] row = (Object[]) em.createNativeQuery("SELECT status, cast(reconciliation AS text) FROM xhandler.sire_period WHERE id = :id FOR UPDATE")
                .setParameter("id", id).getSingleResult();
        return new ReplaceState((String) row[0], (String) row[1]);
    }

    @Override
    public int markSubmitting(UUID id) {
        return em.createNativeQuery("""
                        UPDATE xhandler.sire_period SET status = 'submitting', submitting_since = now(), replacement_ticket = NULL, detail = NULL,
                            poll_attempts = 0, next_poll_at = now(), updated_at = now() WHERE id = :id""")
                .setParameter("id", id).executeUpdate();
    }

    @Override
    public PeriodView getPeriodView(UUID id) {
        Object[] r = (Object[]) em.createNativeQuery("""
                        SELECT id, book, period, sunat_env, status, detail, cast(reconciliation AS text), proposal_ref IS NOT NULL,
                               replacement_ref IS NOT NULL, created_at, updated_at
                        FROM xhandler.sire_period WHERE id = :id""").setParameter("id", id).getSingleResult();
        return new PeriodView((UUID) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4], (String) r[5], (String) r[6],
                (Boolean) r[7], (Boolean) r[8], Times.toInstant(r[9]), Times.toInstant(r[10]));
    }

    @Override
    public List<PeriodSummary> getPeriods(UUID companyId, String sunatEnv, String book, int limit, int offset) {
        return rows(em.createNativeQuery("""
                        SELECT id, book, period, sunat_env, status, detail, proposal_ref IS NOT NULL, replacement_ref IS NOT NULL, created_at,
                               updated_at
                        FROM xhandler.sire_period WHERE company_id = :c AND sunat_env = :env AND (CAST(:b AS text) IS NULL OR book = :b)
                        ORDER BY period DESC, book LIMIT :l OFFSET :o""")
                .setParameter("c", companyId).setParameter("env", sunatEnv).setParameter("b", book).setParameter("l", limit)
                .setParameter("o", offset))
                .stream().map(r -> new PeriodSummary((UUID) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4],
                        (String) r[5], (Boolean) r[6], (Boolean) r[7], Times.toInstant(r[8]), Times.toInstant(r[9]))).toList();
    }

    @Override
    public Optional<String> getProposalRef(UUID id) {
        List<?> ref = em.createNativeQuery("SELECT proposal_ref FROM xhandler.sire_period WHERE id = :id").setParameter("id", id).getResultList();
        return ref.isEmpty() ? Optional.empty() : Optional.ofNullable((String) ref.get(0));
    }

    @Override
    public Optional<String> getReplacementRef(UUID id) {
        List<?> ref = em.createNativeQuery("SELECT replacement_ref FROM xhandler.sire_period WHERE id = :id").setParameter("id", id).getResultList();
        return ref.isEmpty() ? Optional.empty() : Optional.ofNullable((String) ref.get(0));
    }

    @Override
    public Period getPeriod(UUID id) {
        Object[] r = (Object[]) em.createNativeQuery("""
                        SELECT id, company_id, sunat_env, book, period, status, proposal_ticket, replacement_ticket, poll_attempts
                        FROM xhandler.sire_period WHERE id = :id""").setParameter("id", id).getSingleResult();
        return new Period((UUID) r[0], (UUID) r[1], (String) r[2], (String) r[3], (String) r[4], (String) r[5], (String) r[6], (String) r[7],
                ((Number) r[8]).intValue());
    }

    @Override
    public int transitionPeriod(UUID id, String from, String ticketColumn, String ticket, String to, Map<String, Object> columns) {
        StringBuilder sql = new StringBuilder("UPDATE xhandler.sire_period SET status = :to, updated_at = now()");
        for (String column : columns.keySet()) {
            sql.append(", ").append(column).append(" = ");
            sql.append(switch (column) {
                case "reconciliation" -> "cast(:" + column + " AS jsonb)";
                case "next_poll_at" -> "now() + cast(:" + column + " AS interval)";
                default -> ":" + column;
            });
        }
        sql.append(" WHERE id = :id AND status = :from");
        if (ticketColumn != null) {
            sql.append(" AND ").append(ticketColumn).append(" IS NOT DISTINCT FROM :ticket");
        }
        var query = em.createNativeQuery(sql.toString()).setParameter("to", to).setParameter("id", id).setParameter("from", from);
        if (ticketColumn != null) {
            query.setParameter("ticket", ticket);
        }
        columns.forEach(query::setParameter);
        return query.executeUpdate();
    }

    @Override
    public int resumeAfterCredentials(UUID companyId) {
        return em.createNativeQuery("""
                        UPDATE xhandler.sire_period
                        SET status = coalesce(resume_status, 'requested'), resume_status = NULL, poll_attempts = 0, next_poll_at = now(),
                            proposal_ticket = CASE WHEN coalesce(resume_status, 'requested') = 'requested' THEN NULL ELSE proposal_ticket END,
                            detail = NULL, updated_at = now()
                        WHERE company_id = :c AND status = 'credentials'""").setParameter("c", companyId).executeUpdate();
    }

    @Override
    public int claimProposal(UUID id, String claim) {
        return em.createNativeQuery("""
                        UPDATE xhandler.sire_period SET proposal_ticket = :claim, updated_at = now()
                        WHERE id = :id AND status = 'requested'
                          AND (proposal_ticket IS NULL OR proposal_ticket = ''
                               OR (proposal_ticket LIKE 'claim:%' AND updated_at < now() - interval '10 minutes'))""")
                .setParameter("claim", claim).setParameter("id", id).executeUpdate();
    }

    @Override
    public List<PeriodToPoll> getPeriodsToPoll() {
        return rows(em.createNativeQuery("SELECT tenant_id, sire_period_id FROM xhandler.sire_periods_to_poll(50)"))
                .stream().map(r -> new PeriodToPoll((UUID) r[0], (UUID) r[1])).toList();
    }

    @Override
    public List<DeadlineDue> getDeadlinesDue() {
        return rows(em.createNativeQuery("SELECT tenant_id, company_id, ruc, period, due_date FROM xhandler.sire_deadlines_due(3)"))
                .stream().map(r -> new DeadlineDue((UUID) r[0], (UUID) r[1], (String) r[2], (String) r[3],
                        Times.toLocalDate(r[4]))).toList();
    }

    @Override
    public int addDeadlineNotice(UUID tenantId, UUID companyId, String period) {
        return em.createNativeQuery("""
                        INSERT INTO xhandler.sire_deadline_notice (tenant_id, company_id, period) VALUES (:t, :c, :p)
                        ON CONFLICT (company_id, period) DO NOTHING""")
                .setParameter("t", tenantId).setParameter("c", companyId).setParameter("p", period).executeUpdate();
    }

    @Override
    public void putCalendarEntry(String period, int rucLastDigit, LocalDate dueDate) {
        em.createNativeQuery("""
                        INSERT INTO xhandler.sire_calendar (period, ruc_last_digit, due_date) VALUES (:p, :d, :due)
                        ON CONFLICT (period, ruc_last_digit) DO UPDATE SET due_date = excluded.due_date""")
                .setParameter("p", period).setParameter("d", (short) rucLastDigit).setParameter("due", dueDate).executeUpdate();
    }

    @Override
    public List<CalendarEntry> getCalendar(String period) {
        return rows(em.createNativeQuery("""
                        SELECT period, ruc_last_digit, due_date FROM xhandler.sire_calendar
                        WHERE cast(:p AS text) IS NULL OR period = :p ORDER BY period DESC, ruc_last_digit""")
                .setParameter("p", period))
                .stream().map(r -> new CalendarEntry((String) r[0], ((Number) r[1]).intValue(), String.valueOf(r[2]))).toList();
    }
}

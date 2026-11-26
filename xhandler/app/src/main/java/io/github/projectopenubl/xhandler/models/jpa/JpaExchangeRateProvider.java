package io.github.projectopenubl.xhandler.models.jpa;

import io.github.projectopenubl.xhandler.utils.Times;
import io.github.projectopenubl.xhandler.models.ExchangeRateProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Transactional(Transactional.TxType.MANDATORY)
public class JpaExchangeRateProvider implements ExchangeRateProvider {

    private final EntityManager em;

    public JpaExchangeRateProvider(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<ExchangeRate> getLatestRate(LocalDate day, LocalDate oldest) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT day, compra, venta, source FROM xhandler.exchange_rate WHERE day <= :day AND day > :oldest ORDER BY day DESC LIMIT 1")
                .setParameter("oldest", oldest)
                .setParameter("day", day)
                .getResultList();
        return rows.stream().findFirst().map(r -> new ExchangeRate(Times.toLocalDate(r[0]), (BigDecimal) r[1], (BigDecimal) r[2], (String) r[3]));
    }

    @Override
    public void putRate(LocalDate day, BigDecimal compra, BigDecimal venta, String source) {
        em.createNativeStatement("""
                        INSERT INTO xhandler.exchange_rate (day, compra, venta, source) VALUES (:day, :compra, :venta, :source)
                        ON CONFLICT (day) DO UPDATE SET compra = excluded.compra, venta = excluded.venta, source = excluded.source,
                            fetched_at = now()""")
                .setParameter("day", day)
                .setParameter("compra", compra)
                .setParameter("venta", venta)
                .setParameter("source", source)
                .execute();
    }

}

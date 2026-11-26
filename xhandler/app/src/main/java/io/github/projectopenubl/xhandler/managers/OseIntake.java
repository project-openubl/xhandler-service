package io.github.projectopenubl.xhandler.managers;

import io.github.projectopenubl.xhandler.clients.OseClients;
import io.github.projectopenubl.xhandler.utils.ZipGuard;

import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.utils.Deadlines;
import io.github.projectopenubl.xhandler.events.UsageEvents;
import io.github.projectopenubl.xhandler.xml.Engine;
import io.github.projectopenubl.xhandler.xml.EngineModels;
import io.github.projectopenubl.xhandler.keys.PlatformKeys;
import io.github.projectopenubl.xhandler.files.FileStore;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import io.github.projectopenubl.xhandler.models.OseProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The OSE's check of a document (DESIGN.md §11.4; RS 117-2017 Annex B and C): the issuer is
 * authenticated, the ZIP hardened, blocked and unassociated issuers refused, the document validated
 * in full (no shortcut for documents the platform signed as PSE), and the answer is either a CDR-OSE
 * signed with the platform's OSE key or an inconsistency notice. Every answer is recorded in
 * {@code ose_submission}; CDRs are queued for forwarding to SUNAT within 1 hour.
 *
 * <p>{@code sendBill} answers at once; {@code sendSummary} and {@code sendPack} answer with a
 * ticket for {@code getStatus}. Validation runs before answering in every case.
 */
@ApplicationScoped
public class OseIntake {

    /** OSE files live outside any tenant's namespace. */
    static final UUID STORAGE = UUID.fromString("00000000-0000-0000-0000-00000000005e");
    static final Duration RETENTION = Duration.ofDays(31);
    static final int MAX_PACK = 500;

    private static final Pattern BILL = Pattern.compile("^(\\d{11})-(\\d{2})-([A-Z0-9]{4})-(\\d{1,8})$");
    private static final Pattern SUMMARY = Pattern.compile("^(\\d{11})-(RC|RA|RR)-(\\d{8})-(\\d{1,5})$");
    private static final List<String> TIPOS = List.of("01", "03", "07", "08", "20", "40");
    private static final SecureRandom RANDOM = new SecureRandom();

    /** An answer that is a SOAP fault (or a REST 422): SUNAT's code and message. */
    public static final class Fault extends RuntimeException {
        public final String code;

        public Fault(String code, String message) {
            super(message);
            this.code = code;
        }
    }

    /** The CDR-OSE of one document. */
    public record Cdr(String name, String cdrXml) {
        public byte[] zip() {
            return OseIntake.zip(Map.of("R-" + name + ".xml", cdrXml.getBytes(StandardCharsets.UTF_8)));
        }
    }

    /** {@code statusCode}: 0 done, 98 in process, 99 with inconsistencies (SUNAT's getStatus codes). */
    public record Status(String statusCode, byte[] content) {}

    private final OseProvider oseProvider;
    private final Engine engine;
    private final PlatformKeys platformKeys;
    private final FileStore files;
    private final UsageEvents usage;
    private final TenantAccess access;
    private final MeterRegistry metrics;
    private final Optional<String> oseRuc;
    private final String environment;

    public OseIntake(OseProvider oseProvider, Engine engine, PlatformKeys platformKeys, FileStore files, UsageEvents usage, TenantAccess access,
            MeterRegistry metrics, @ConfigProperty(name = "xhandler.ose.ruc") Optional<String> oseRuc,
            @ConfigProperty(name = "xhandler.ose.environment", defaultValue = "fake") String environment) {
        this.oseProvider = oseProvider;
        this.engine = engine;
        this.platformKeys = platformKeys;
        this.files = files;
        this.usage = usage;
        this.access = access;
        this.metrics = metrics;
        this.oseRuc = oseRuc;
        this.environment = environment;
    }

    record Name(String full, String ruc, String tipo, String serie, long numero, boolean summary) {}

    private static Name name(String fileName, boolean summary) {
        String base = fileName == null ? "" : fileName.replaceFirst("(?i)\\.(zip|xml)$", "");
        Matcher m = (summary ? SUMMARY : BILL).matcher(base);
        if (!m.matches()) {
            throw new Fault("0151", "El nombre del archivo ZIP es incorrecto.");
        }
        return new Name(base, m.group(1), m.group(2), m.group(3), Long.parseLong(m.group(4)), summary);
    }

    private String ruc() {
        return oseRuc.orElseThrow(() -> new Fault("0109", "El sistema no puede responder su solicitud. (Servicio no disponible)"));
    }

    /** The issuer must have chosen us as its OSE in SOL; the relation counts until the 7th day of the month after it ends. */
    private void checkIssuer(OseClients.Client client, Name name) {
        if (!client.issuerRuc().equals(name.ruc())) {
            throw new Fault("0111", "No tiene el perfil para enviar comprobantes electrónicos de este emisor.");
        }
        QuarkusTransaction.requiringNew().run(() -> {
            LocalDate today = Deadlines.todayInLima();
            boolean associated = oseProvider.isAssociated(name.ruc(), today);
            if (!associated) {
                throw new Fault("0154", "El emisor no ha autorizado a este OSE en SUNAT Operaciones en Línea.");
            }
            if (oseProvider.isBlocked(name.ruc())) {
                throw new Fault("2010", "El emisor está impedido de emitir comprobantes electrónicos (listas de SUNAT).");
            }
        });
    }

    /** One comprobante: the CDR-OSE at once, or a fault. */
    public Cdr sendBill(OseClients.Client client, String channel, String fileName, byte[] zip) {
        Name name = name(fileName, false);
        ruc();
        checkIssuer(client, name);
        Map<String, byte[]> xmls = ZipGuard.xmlFiles(zip, 1);
        byte[] xml = xmls.get(name.full() + ".xml");
        if (xml == null) {
            throw new Fault("0151", "El nombre del XML no corresponde al del ZIP.");
        }
        Checked checked = check(client, channel, name, xml, null);
        if (checked.cdr() == null) {
            throw new Fault(checked.code(), checked.message());
        }
        return checked.cdr();
    }

    /** A resumen diario or comunicación de baja: checked now, answered through the ticket. */
    public String sendSummary(OseClients.Client client, String channel, String fileName, byte[] zip) {
        Name name = name(fileName, true);
        ruc();
        checkIssuer(client, name);
        Map<String, byte[]> xmls = ZipGuard.xmlFiles(zip, 1);
        byte[] xml = xmls.get(name.full() + ".xml");
        if (xml == null) {
            throw new Fault("0151", "El nombre del XML no corresponde al del ZIP.");
        }
        String ticket = ticket();
        check(client, channel, name, xml, ticket);
        return ticket;
    }

    /** A batch of comprobantes of one issuer: one CDR-OSE per XML, through the ticket. */
    public String sendPack(OseClients.Client client, String channel, String fileName, byte[] zip) {
        ruc();
        Map<String, byte[]> xmls = ZipGuard.xmlFiles(zip, MAX_PACK);
        String ticket = ticket();
        boolean any = false;
        for (Map.Entry<String, byte[]> xml : xmls.entrySet()) {
            // Each document gets its answer in the ticket; one bad entry doesn't lose the others
            Name name;
            try {
                name = name(xml.getKey(), false);
            } catch (Fault e) {
                name = new Name(xml.getKey().replaceFirst("(?i)\\.xml$", ""), client.issuerRuc(), "??", "????", 0, false);
                record(client, channel, name, ticket, Instant.now(), FileStore.sha256(xml.getValue()), null, null, e.code, e.getMessage());
                any = true;
                continue;
            }
            try {
                checkIssuer(client, name);
            } catch (Fault e) {
                record(client, channel, name, ticket, Instant.now(), FileStore.sha256(xml.getValue()), null, null, e.code, e.getMessage());
                any = true;
                continue;
            }
            check(client, channel, name, xml.getValue(), ticket);
            any = true;
        }
        if (!any) {
            throw new Fault("0157", "El archivo ZIP no contiene comprobantes.");
        }
        return ticket;
    }

    private static String ticket() {
        return System.currentTimeMillis() + String.format("%03d", RANDOM.nextInt(1000));
    }

    record Checked(Cdr cdr, String code, String message) {}

    /** Validates one document and records the answer. */
    private Checked check(OseClients.Client client, String channel, Name name, byte[] xmlBytes, String ticket) {
        // In the encoding it declares: the engine turns it back into these exact bytes
        String xml = io.github.projectopenubl.xhandler.utils.XmlText.decode(xmlBytes);
        String sha256 = FileStore.sha256(xmlBytes);
        Instant received = Instant.now();
        // A resend of an accepted document: the same bytes get the same CDR; other bytes collide
        Optional<OseProvider.AcceptedSubmission> previous = QuarkusTransaction.requiringNew()
                .call(() -> oseProvider.getAcceptedSubmission(name.ruc(), name.tipo(), name.serie(), name.numero()));
        if (previous.isPresent()) {
            if (sha256.equals(previous.get().sha256())) {
                if (ticket != null) {
                    // The ticket answers for it too
                    link(ticket, client, previous.get().id());
                }
                return new Checked(new Cdr(name.full(), new String(files.get(previous.get().cdrRef()), StandardCharsets.UTF_8)), null, null);
            }
            return record(client, channel, name, ticket, received, sha256, null, null, "1033",
                    "El comprobante fue registrado previamente con otros datos.");
        }
        if (!name.summary() && !TIPOS.contains(name.tipo())) {
            String message = "09".equals(name.tipo()) || "31".equals(name.tipo())
                    ? "Las guías de remisión se envían directamente a SUNAT." : "Tipo de comprobante no admitido por el OSE.";
            return record(client, channel, name, ticket, received, sha256, null, null, "1003", message);
        }
        EngineModels.InspectResponse identity;
        try {
            identity = engine.inspect(xml);
        } catch (io.github.projectopenubl.xhandler.xml.EngineException e) {
            if (!"invalid_document".equals(e.kind())) {
                // The engine failed, not the document: nothing is recorded or billed, the issuer retries
                throw new Fault("0109", "El sistema no puede responder su solicitud.");
            }
            return record(client, channel, name, ticket, received, sha256, null, null, "0306", "No se puede leer (parsear) el archivo XML.");
        }
        if (!name.ruc().equals(identity.ruc()) || !name.summary() && (!name.serie().equals(identity.serie())
                || identity.numero() == null || identity.numero() != name.numero() || !name.tipo().equals(identity.tipoComprobante()))) {
            return record(client, channel, name, ticket, received, sha256, null, null, "0151",
                    "El nombre del archivo no corresponde al contenido del comprobante.");
        }
        if (!identity.signed()) {
            return record(client, channel, name, ticket, received, sha256, null, null, "0306", "El comprobante no tiene firma digital.");
        }
        EngineModels.ValidateResponse validation = engine.validate(xml);
        if (!validation.valid()) {
            EngineModels.Finding first = validation.errors().isEmpty() ? new EngineModels.Finding("0306", "Documento inválido")
                    : validation.errors().get(0);
            boolean numbered = first.code() != null && first.code().matches("\\d{4}");
            // XSD messages come from libxml2 in English: the issuer gets SUNAT's code and a Spanish text
            String message = numbered ? truncate(first.message()) : "El XML no cumple el esquema (XSD) de SUNAT.";
            return record(client, channel, name, ticket, received, sha256, null, null, numbered ? first.code() : "0306", message);
        }
        List<EngineModels.CdrNote> notes = validation.observations() == null ? List.of()
                : validation.observations().stream().map(o -> new EngineModels.CdrNote(o.code(), truncate(o.message()))).toList();
        String description = switch (name.tipo()) {
            case "RC" -> "El resumen diario ";
            case "RA" -> "La comunicación de baja ";
            case "RR" -> "El resumen de reversiones ";
            default -> "El comprobante ";
        } + identity.serieNumero() + " ha sido aceptado";
        // One instant: the CDR's response time and the start of the 1-hour forwarding clock
        Instant checkedAt = Instant.now();
        String cdrXml = signCdr(engine.cdr(xml, ruc(), "0", description, notes, checkedAt));
        FileStore.Stored storedXml = files.put(STORAGE, xmlBytes);
        FileStore.Stored storedCdr = files.put(STORAGE, cdrXml.getBytes(StandardCharsets.UTF_8));
        return record(client, channel, name, ticket, received, sha256, new String[] {storedXml.key(), storedCdr.key(), cdrXml}, checkedAt, "0",
                description);
    }

    /** CDR-OSE signed with the platform's OSE key; the demo key outside production when there's none. */
    private String signCdr(String unsigned) {
        Optional<PlatformKeys.Key> key = platformKeys.active("ose_cdr");
        if (key.isEmpty()) {
            if ("production".equals(environment)) {
                throw new Fault("0109", "El sistema no puede responder su solicitud. (Servicio no disponible)");
            }
            return engine.signWithDemoKey(unsigned).xml();
        }
        EngineModels.SignPrepareResponse prepared = engine.signPrepare(unsigned);
        byte[] signature = platformKeys.sign(key.get(), Base64.getDecoder().decode(prepared.dataToSign()));
        return engine.signFinalize(unsigned, signature, key.get().certificate()).xml();
    }

    private void link(String ticket, OseClients.Client client, UUID submission) {
        QuarkusTransaction.requiringNew().run(() -> oseProvider.linkTicketItem(ticket, client.id(), submission));
    }

    private Checked record(OseClients.Client client, String channel, Name name, String ticket, Instant received, String sha256, String[] cdr,
            Instant checkedAt, String code, String description) {
        boolean accepted = cdr != null;
        UUID id = Ids.newId();
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                Instant now = checkedAt != null ? checkedAt : Instant.now();
                oseProvider.addSubmission(new OseProvider.NewSubmission(id, client.id(), name.ruc(), name.tipo(), name.serie(), name.numero(),
                        name.full(), channel, ticket, received, accepted ? "cdr" : "inconsistency", code, description, sha256,
                        accepted ? cdr[0] : null, accepted ? cdr[1] : null, accepted ? now : null, accepted ? "pending" : null,
                        accepted ? now : null, now.plus(RETENTION)));
                if (ticket != null) {
                    oseProvider.addTicketItem(ticket, client.id(), id);
                }
                oseProvider.countValidation(client.id(), Deadlines.todayInLima());
                if (client.tenantId() != null) {
                    // Its own billing line (RS 242-2018 art. 7.16)
                    access.enterAsPlatform(client.tenantId());
                    usage.record(client.tenantId(), "ose_validation", Map.of("issuer_ruc", name.ruc(), "result", accepted ? "cdr" : "inconsistency"));
                }
            });
        } catch (RuntimeException e) {
            if (accepted && io.github.projectopenubl.xhandler.utils.Constraints.violated(e).isPresent()) {
                // Two sends of one number at once: the first one's CDR stands; the same bytes get it
                Optional<OseProvider.AcceptedSubmission> winner = QuarkusTransaction.requiringNew()
                        .call(() -> oseProvider.getAcceptedSubmission(name.ruc(), name.tipo(), name.serie(), name.numero()));
                if (winner.isPresent() && sha256.equals(winner.get().sha256())) {
                    if (ticket != null) {
                        link(ticket, client, winner.get().id());
                    }
                    return new Checked(new Cdr(name.full(), new String(files.get(winner.get().cdrRef()), StandardCharsets.UTF_8)), null, null);
                }
                throw new Fault("1033", "El comprobante fue registrado previamente con otros datos.");
            }
            throw e;
        }
        metrics.counter("xhandler.ose.submissions", "result", accepted ? "cdr" : "inconsistency").increment();
        return new Checked(accepted ? new Cdr(name.full(), cdr[2]) : null, code, description);
    }

    /** The answer behind a ticket of this client. */
    public Status getStatus(OseClients.Client client, String ticket) {
        List<OseProvider.TicketItem> rows = QuarkusTransaction.requiringNew().call(() -> oseProvider.getTicketItems(ticket, client.id()));
        if (rows.isEmpty()) {
            throw new Fault("0127", "El ticket no existe.");
        }
        Map<String, byte[]> content = new LinkedHashMap<>();
        StringBuilder errors = new StringBuilder();
        for (OseProvider.TicketItem r : rows) {
            if ("cdr".equals(r.result())) {
                content.put("R-" + r.name() + ".xml", files.get(r.cdrRef()));
            } else {
                errors.append(r.name()).append('|').append(r.responseCode()).append('|').append(r.description()).append('\n');
            }
        }
        if (!errors.isEmpty()) {
            content.put("errores.txt", errors.toString().getBytes(StandardCharsets.UTF_8));
        }
        return new Status(errors.isEmpty() ? "0" : "99", zip(content));
    }

    /** The CDR-OSE of a document this client sent, if accepted. */
    public Optional<Cdr> cdrOf(OseClients.Client client, String ruc, String tipo, String serie, long numero) {
        if (!client.issuerRuc().equals(ruc)) {
            return Optional.empty();
        }
        return QuarkusTransaction.requiringNew().call(() -> oseProvider.getSubmissionCdr(ruc, tipo, serie, numero))
                .map(r -> new Cdr(r.name(), new String(files.get(r.cdrRef()), StandardCharsets.UTF_8)));
    }

    static byte[] zip(Map<String, byte[]> entries) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(e.getKey()));
                out.write(e.getValue());
                out.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    private static String truncate(String text) {
        return text == null ? "" : text.length() > 250 ? text.substring(0, 250) : text;
    }

    static List<String> names(Map<String, byte[]> files) {
        return new ArrayList<>(files.keySet());
    }
}

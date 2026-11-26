package io.github.projectopenubl.xhandler.managers;

import io.github.projectopenubl.xhandler.models.DocumentProvider;
import io.github.projectopenubl.xhandler.models.DocumentFileProvider;
import io.github.projectopenubl.xhandler.models.DocumentEventProvider;
import io.github.projectopenubl.xhandler.models.DocumentModel;
import io.github.projectopenubl.xhandler.models.DocumentEventModel;
import io.github.projectopenubl.xhandler.models.DocumentFileModel;
import io.github.projectopenubl.xhandler.utils.Deadlines;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.xml.Engine;
import io.github.projectopenubl.xhandler.xml.EngineModels;
import io.github.projectopenubl.xhandler.keys.SignatureLog;
import io.github.projectopenubl.xhandler.keys.SigningService;
import io.github.projectopenubl.xhandler.files.FileStore;
import io.github.projectopenubl.xhandler.security.TenantScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds, numbers, signs and stores summaries: resumen diario (RC) and comunicación de baja (RA)
 * (DESIGN.md §8, §9.2.4, §11.3). Like emit, this runs in one transaction with the company and
 * daily counter locked, and it is the same documented exception to rule 3 of §10.8.
 */
@ApplicationScoped
public class Summaries {

    private final DocumentProvider documentProvider;

    private final DocumentFileProvider documentFileProvider;

    private final DocumentEventProvider documentEventProvider;

    /** SUNAT accepts at most 500 lines per summary. */
    public static final int MAX_LINES = 500;

    static final DateTimeFormatter BASIC = DateTimeFormatter.BASIC_ISO_DATE;

    private final Numbering numbering;
    private final Engine engine;
    private final SigningService signing;
    private final SignatureLog signatureLog;
    private final FileStore files;
    private final Routes routes;
    private final EntityManager em;
    private final ObjectMapper json;

    public Summaries(Numbering numbering, Engine engine, SigningService signing, SignatureLog signatureLog, FileStore files,
            Routes routes, EntityManager em, ObjectMapper json, DocumentEventProvider documentEventProvider, DocumentFileProvider documentFileProvider, DocumentProvider documentProvider) {
        this.documentProvider = documentProvider;
        this.documentFileProvider = documentFileProvider;
        this.documentEventProvider = documentEventProvider;
        this.numbering = numbering;
        this.engine = engine;
        this.signing = signing;
        this.signatureLog = signatureLog;
        this.files = files;
        this.routes = routes;
        this.em = em;
        this.json = json;
    }

    /**
     * A resumen diario for documents of one issue day. {@code operacion}: 1 adds them (the D+5
     * fallback), 3 voids them (§11.3). The documents must be locked by the caller.
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public DocumentModel resumenDiario(TenantScope scope, CompanyModel company, String sunatEnv, LocalDate fechaComprobantes,
            List<DocumentModel> lines, String operacion, String correlationId) {
        LocalDate day = Deadlines.todayInLima();
        int numero = numbering.reserveDaily(company.getTenantId(), company.getId(), sunatEnv, "RC", day);
        return resumenDiario(scope, company, sunatEnv, fechaComprobantes, lines, operacion, correlationId, day, numero);
    }

    /** RC number for today, reserved before the caller locks the lines (lock order company → counter → documents). */
    @Transactional(Transactional.TxType.MANDATORY)
    public int reserveResumenDiario(CompanyModel company, String sunatEnv) {
        return numbering.reserveDaily(company.getTenantId(), company.getId(), sunatEnv, "RC", Deadlines.todayInLima());
    }

    /** A resumen diario with a number already reserved for {@code day}. */
    @Transactional(Transactional.TxType.MANDATORY)
    public DocumentModel resumenDiario(TenantScope scope, CompanyModel company, String sunatEnv, LocalDate fechaComprobantes,
            List<DocumentModel> lines, String operacion, String correlationId, LocalDate day, int numero) {
        if (lines.isEmpty() || lines.size() > MAX_LINES) {
            throw new IllegalArgumentException("a resumen diario has 1 to " + MAX_LINES + " lines");
        }
        ObjectNode spec = json.createObjectNode();
        spec.put("numero", numero);
        spec.put("fecha_emision", day.toString());
        spec.put("fecha_emision_comprobantes", fechaComprobantes.toString());
        proveedor(spec, company);
        ArrayNode comprobantes = spec.putArray("comprobantes");
        for (DocumentModel line : lines) {
            comprobantes.add(rcLine(line, operacion));
        }
        DocumentModel rc = summary(scope, company, sunatEnv, "SummaryDocuments", "RC", day, numero, spec, correlationId,
                "3".equals(operacion) ? lines.get(0) : null);
        rc.setDeadlineAt(Deadlines.sendDeadline("RC", "RC", fechaComprobantes));
        return rc;
    }

    /** A comunicación de baja (RA) voiding the given documents of one issue day (§11.3). */
    @Transactional(Transactional.TxType.MANDATORY)
    public DocumentModel comunicacionBaja(TenantScope scope, CompanyModel company, String sunatEnv, LocalDate fechaComprobantes,
            List<DocumentModel> originals, String motivo, String correlationId) {
        LocalDate day = Deadlines.todayInLima();
        int numero = numbering.reserveDaily(company.getTenantId(), company.getId(), sunatEnv, "RA", day);
        ObjectNode spec = json.createObjectNode();
        spec.put("numero", numero);
        spec.put("fecha_emision", day.toString());
        spec.put("fecha_emision_comprobantes", fechaComprobantes.toString());
        proveedor(spec, company);
        ArrayNode comprobantes = spec.putArray("comprobantes");
        for (DocumentModel original : originals) {
            ObjectNode item = comprobantes.addObject();
            item.put("tipo_comprobante", original.getTipoComprobante());
            item.put("serie", original.getSerie());
            item.put("numero", original.getNumero());
            item.put("descripcion_sustento", motivo);
        }
        DocumentModel ra = summary(scope, company, sunatEnv, "VoidedDocuments", "RA", day, numero, spec, correlationId, originals.get(0));
        ra.setDeadlineAt(Deadlines.endOfDay(day.plusDays(7)));
        return ra;
    }

    /** A resumen de reversiones (RR) for percepciones and retenciones (§3.1). */
    @Transactional(Transactional.TxType.MANDATORY)
    public DocumentModel resumenReversiones(TenantScope scope, CompanyModel company, String sunatEnv, LocalDate fechaComprobantes,
            List<DocumentModel> originals, String motivo, String correlationId) {
        LocalDate day = Deadlines.todayInLima();
        int numero = numbering.reserveDaily(company.getTenantId(), company.getId(), sunatEnv, "RR", day);
        ObjectNode spec = json.createObjectNode();
        spec.put("numero", numero);
        spec.put("fecha_emision", day.toString());
        spec.put("fecha_emision_comprobantes", fechaComprobantes.toString());
        proveedor(spec, company);
        ArrayNode comprobantes = spec.putArray("comprobantes");
        for (DocumentModel original : originals) {
            ObjectNode item = comprobantes.addObject();
            item.put("tipo_comprobante", original.getTipoComprobante());
            item.put("serie", original.getSerie());
            item.put("numero", original.getNumero());
            item.put("descripcion_sustento", motivo);
        }
        DocumentModel rr = summary(scope, company, sunatEnv, "Reversion", "RR", day, numero, spec, correlationId, originals.get(0));
        rr.setDeadlineAt(Deadlines.endOfDay(day.plusDays(7)));
        return rr;
    }

    private DocumentModel summary(TenantScope scope, CompanyModel company, String sunatEnv, String kind, String tipo, LocalDate day, int numero,
            ObjectNode spec, String correlationId) {
        return summary(scope, company, sunatEnv, kind, tipo, day, numero, spec, correlationId, null);
    }

    private DocumentModel summary(TenantScope scope, CompanyModel company, String sunatEnv, String kind, String tipo, LocalDate day, int numero,
            ObjectNode spec, String correlationId, DocumentModel followsRouteOf) {
        ObjectNode document = json.createObjectNode();
        document.put("kind", kind);
        document.set("spec", spec);
        EngineModels.BuildResponse built = engine.build(document);
        EngineModels.ValidateResponse validation = engine.validate(built.xml());
        if (!validation.valid()) {
            String detail = validation.errors().stream().map(f -> f.code() + ": " + f.message()).collect(Collectors.joining("; "));
            throw ApiException.invalid("invalid_document", "El resumen no cumple las validaciones de SUNAT.", detail);
        }
        SigningService.Signed signed = signing.sign(company, sunatEnv, built.xml());
        DocumentModel doc = documentProvider.newDocument(Ids.newId());
        String serie = tipo + "-" + BASIC.format(day);
        signatureLog.record(company.getTenantId(), doc.getId(), serie + "-" + numero, signed.signingKeyId(), signed.platformKeyId(), signed.digestValue(),
                scope.actor().auditName());
        FileStore.Stored xml = files.put(company.getTenantId(), signed.xml().getBytes(StandardCharsets.UTF_8));
        FileStore.Stored input = files.put(company.getTenantId(), bytes(document));

        Instant now = Instant.now();
        doc.setTenantId(company.getTenantId());
        doc.setCompanyId(company.getId());
        doc.setSunatEnv(sunatEnv);
        doc.setOrigin("summary");
        doc.setKind(kind);
        doc.setTipoComprobante(tipo);
        doc.setSerie(serie);
        doc.setNumero(numero);
        doc.setFechaEmision(day);
        doc.setIssueYear((short) day.getYear());
        doc.setDeadlineAt(Deadlines.endOfDay(day.plusDays(7)));
        doc.setStatus("pending_send");
        doc.setDigestValue(signed.digestValue());
        Routes.Assignment route = followsRouteOf == null
                ? routes.assign(company, sunatEnv, tipo)
                : routes.assignLike(company, sunatEnv, tipo, followsRouteOf);
        doc.setEndpoint(route.endpoint());
        doc.setCredentialId(route.credentialId());
        doc.setEmissionMode(company.getEmissionMode());
        doc.setNextAttemptAt(now);
        doc.setProgressAt(now);
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        documentProvider.persist(doc);
        documentFileProvider.addDocumentFile(doc, "xml_signed", xml);
        documentFileProvider.addDocumentFile(doc, "input", input);
        documentEventProvider.addDocumentEvent(doc, null, "emitted", scope.actor().auditName(), correlationId);
        em.createNativeQuery("SELECT pg_notify('xhandler_reconcile', '')").getSingleResult();
        return doc;
    }

    /** One RC line from a boleta's (or B-note's) enriched JSON. */
    private ObjectNode rcLine(DocumentModel line, String operacion) {
        JsonNode spec = enriched(line).path("spec");
        JsonNode totals = spec.path("total_impuestos");
        ObjectNode item = json.createObjectNode();
        item.put("tipo_operacion", operacion);
        ObjectNode comprobante = item.putObject("comprobante");
        comprobante.put("tipo_comprobante", line.getTipoComprobante());
        comprobante.put("serie_numero", line.serieNumero());
        if (spec.hasNonNull("moneda")) {
            comprobante.put("moneda", spec.get("moneda").asText());
        }
        ObjectNode cliente = comprobante.putObject("cliente");
        cliente.put("numero_documento_identidad", spec.path("cliente").path("numero_documento_identidad").asText("0"));
        cliente.put("tipo_documento_identidad", spec.path("cliente").path("tipo_documento_identidad").asText("0"));
        if (spec.hasNonNull("comprobante_afectado_serie_numero")) {
            ObjectNode afectado = comprobante.putObject("comprobante_afectado");
            afectado.put("tipo_comprobante", spec.path("comprobante_afectado_tipo").asText("03"));
            afectado.put("serie_numero", spec.get("comprobante_afectado_serie_numero").asText());
        }
        ObjectNode valorVenta = comprobante.putObject("valor_venta");
        valorVenta.set("importe_total", spec.path("total_importe").path("importe"));
        copy(totals, "gravado_base_imponible", valorVenta, "gravado");
        copy(totals, "exonerado_base_imponible", valorVenta, "exonerado");
        copy(totals, "inafecto_base_imponible", valorVenta, "inafecto");
        copy(totals, "gratuito_base_imponible", valorVenta, "gratuito");
        ObjectNode impuestos = comprobante.putObject("impuestos");
        copy(totals, "gravado_importe", impuestos, "igv");
        copy(totals, "icb_importe", impuestos, "icb");
        copy(totals, "isc_importe", impuestos, "isc");
        copy(totals, "ivap_importe", impuestos, "ivap");
        return item;
    }

    private JsonNode enriched(DocumentModel document) {
        DocumentFileModel file = documentFileProvider.getDocumentFile(document.getId(), "enriched").orElseThrow(() ->
                new IllegalStateException("document " + document.getId() + " has no enriched JSON"));
        try {
            return json.readTree(files.get(file.getStorageKey()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void copy(JsonNode from, String field, ObjectNode to, String target) {
        JsonNode value = from.get(field);
        if (value != null && !value.isNull()) {
            to.set(target, value);
        }
    }

    private static void proveedor(ObjectNode spec, CompanyModel company) {
        ObjectNode proveedor = spec.putObject("proveedor");
        proveedor.put("ruc", company.getRuc());
        proveedor.put("razon_social", company.getRazonSocial());
    }

    private byte[] bytes(JsonNode node) {
        try {
            return json.writeValueAsBytes(node);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

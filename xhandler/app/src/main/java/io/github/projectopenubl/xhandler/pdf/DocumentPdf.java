package io.github.projectopenubl.xhandler.pdf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.projectopenubl.xhandler.models.jpa.entities.CompanyEntity;
import io.github.projectopenubl.xhandler.models.jpa.entities.DocumentEntity;
import io.github.projectopenubl.xhandler.models.jpa.entities.DocumentFileEntity;
import io.github.projectopenubl.xhandler.utils.Qr;
import io.github.projectopenubl.xhandler.files.FileStore;
import io.github.projectopenubl.xhandler.models.jpa.entities.TenantEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The PDF of a document (DESIGN.md §11.6): rendered from its enriched JSON (totals and legends come
 * from xhandler-rust, never computed here), the company and its status. Rendered on demand, so the
 * "pendiente" banner always reflects the current status. Runs with the document's tenant open.
 */
@ApplicationScoped
public class DocumentPdf {

    static final Map<String, String> TIPOS = Map.of(
            "01", "FACTURA ELECTRÓNICA", "03", "BOLETA DE VENTA ELECTRÓNICA", "07", "NOTA DE CRÉDITO ELECTRÓNICA",
            "08", "NOTA DE DÉBITO ELECTRÓNICA", "09", "GUÍA DE REMISIÓN REMITENTE ELECTRÓNICA",
            "31", "GUÍA DE REMISIÓN TRANSPORTISTA ELECTRÓNICA", "20", "COMPROBANTE DE RETENCIÓN ELECTRÓNICO",
            "40", "COMPROBANTE DE PERCEPCIÓN ELECTRÓNICO");

    static final Map<String, String> DOC_IDENTIDAD = Map.of("0", "Doc. trib. no dom.", "1", "DNI", "4", "Carné de extranjería",
            "6", "RUC", "7", "Pasaporte", "A", "Cédula diplomática", "-", "Documento");

    private final FileStore files;
    private final PdfRenderer renderer;
    private final ObjectMapper json;
    private final EntityManager em;

    public DocumentPdf(FileStore files, PdfRenderer renderer, ObjectMapper json, EntityManager em) {
        this.files = files;
        this.renderer = renderer;
        this.json = json;
        this.em = em;
    }

    /** PDF bytes; {@code accessUrl} (the buyer link) is printed when given. */
    @Transactional(Transactional.TxType.MANDATORY)
    public byte[] render(UUID documentId, String accessUrl) {
        DocumentEntity doc = DocumentEntity.findById(documentId);
        CompanyEntity company = CompanyEntity.findById(doc.getCompanyId());
        JsonNode spec = enriched(doc).path("spec");
        Map<String, Object> d = new HashMap<>();
        d.put("tipoNombre", TIPOS.getOrDefault(doc.getTipoComprobante(), "COMPROBANTE ELECTRÓNICO"));
        d.put("serieNumero", doc.serieNumero());
        d.put("fechaEmision", doc.getFechaEmision().toString());
        d.put("moneda", doc.getMoneda() == null ? spec.path("moneda").asText("PEN") : doc.getMoneda());
        JsonNode cliente = spec.has("cliente") ? spec.path("cliente") : spec.path("destinatario");
        d.put("clienteNombre", text(cliente.path("nombre")));
        d.put("clienteNumero", text(cliente.path("numero_documento_identidad")));
        d.put("clienteTipoNombre", DOC_IDENTIDAD.getOrDefault(cliente.path("tipo_documento_identidad").asText("-"), "Documento"));
        d.put("afectado", text(spec.path("comprobante_afectado_serie_numero")));
        d.put("motivo", text(spec.path("descripcion_sustento")));
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode detalle : spec.path("detalles")) {
            Map<String, Object> item = new HashMap<>();
            item.put("descripcion", detalle.path("descripcion").asText(""));
            item.put("cantidad", plain(detalle.path("cantidad")));
            // The engine's per-line price with every tax (IGV, ISC, ICBPER); free lines are worth 0
            boolean gratuito = detalle.path("igv_tipo").asText("").matches("1[1-7]|21|3[1-7]");
            BigDecimal unit = gratuito ? BigDecimal.ZERO
                    : decimal(detalle.has("precio_con_impuestos") ? detalle.path("precio_con_impuestos") : detalle.path("precio"));
            item.put("precio", money(unit));
            item.put("importe", money(unit.multiply(decimal(detalle.path("cantidad")))));
            items.add(item);
        }
        d.put("items", items);
        JsonNode totals = spec.path("total_impuestos");
        d.put("gravado", nonZero(totals.path("gravado_base_imponible")));
        d.put("exonerado", nonZero(totals.path("exonerado_base_imponible")));
        d.put("inafecto", nonZero(totals.path("inafecto_base_imponible")));
        d.put("igv", nonZero(totals.path("gravado_importe")));
        d.put("total", doc.getTotal() != null ? money(doc.getTotal()) : money(decimal(spec.path("total_importe").path("importe"))));
        d.put("leyenda", text(spec.path("leyendas").path("1000")));
        d.put("digest", doc.getDigestValue());
        d.put("estado", "production".equals(doc.getSunatEnv()) ? estado(doc.getStatus()) : "documento de prueba, sin valor legal");

        Map<String, Object> data = new HashMap<>();
        data.put("d", d);
        data.put("c", Map.of("ruc", company.getRuc(), "razonSocial", company.getRazonSocial(),
                "nombreComercial", company.getNombreComercial() == null ? "" : company.getNombreComercial(),
                "direccion", company.getDireccion() == null ? "" : company.getDireccion(), "email", company.getEmail() == null ? "" : company.getEmail()));
        data.put("brand", branding(company));
        data.put("logo", logo(company));
        data.put("qr", PdfRenderer.qrDataUri(Qr.of(company.getRuc(), doc, enriched(doc))));
        data.put("accessUrl", accessUrl);
        data.put("banner", "production".equals(doc.getSunatEnv()) ? banner(doc.getStatus()) : "SIN VALOR LEGAL - DOCUMENTO DE PRUEBA");
        return renderer.render(company.getPdfFormat() == null ? "a4" : company.getPdfFormat(), data);
    }

    /** Until SUNAT's definitive answer the PDF says so (§11.6). */
    static String banner(String status) {
        return switch (status) {
            case "accepted", "accepted_with_observations" -> null;
            case "rejected" -> "DOCUMENTO RECHAZADO POR SUNAT";
            case "voided" -> "DOCUMENTO ANULADO";
            case "voiding" -> "ANULACIÓN EN PROCESO";
            default -> "PENDIENTE DE RESPUESTA DE SUNAT";
        };
    }

    static String estado(String status) {
        return switch (status) {
            case "accepted" -> "aceptado";
            case "accepted_with_observations" -> "aceptado con observaciones";
            case "rejected" -> "rechazado";
            case "voided" -> "anulado";
            case "voiding" -> "en anulación";
            default -> "pendiente";
        };
    }

    /** Partner branding (white-label): color, footer, name. */
    private Map<String, Object> branding(CompanyEntity company) {
        Map<String, Object> brand = new HashMap<>(Map.of("color", "#1f4e79", "footer", ""));
        TenantEntity tenant = TenantEntity.findById(company.getTenantId());
        if (tenant != null && tenant.getPartnerId() != null) {
            Object raw = em.createNativeQuery("SELECT CAST(branding AS text) FROM xhandler.partner WHERE id = :id")
                    .setParameter("id", tenant.getPartnerId())
                    .getResultStream().findFirst().orElse(null);
            if (raw != null) {
                try {
                    JsonNode node = json.readTree(raw.toString());
                    if (node.hasNonNull("color") && node.get("color").asText().matches("#[0-9a-fA-F]{6}")) {
                        brand.put("color", node.get("color").asText());
                    }
                    if (node.hasNonNull("footer")) {
                        brand.put("footer", node.get("footer").asText());
                    }
                } catch (IOException e) {
                    // Bad branding never blocks a PDF
                }
            }
        }
        if (company.getPdfOptions() != null && company.getPdfOptions().get("color") instanceof String color && color.matches("#[0-9a-fA-F]{6}")) {
            brand.put("color", color);
        }
        return brand;
    }

    /** The company's logo, else its partner's (white-label), else none. */
    private String logo(CompanyEntity company) {
        String ref = company.getLogoRef();
        if (ref == null) {
            TenantEntity tenant = TenantEntity.findById(company.getTenantId());
            if (tenant != null && tenant.getPartnerId() != null) {
                List<?> partnerLogo = em.createNativeQuery("SELECT logo_ref FROM xhandler.partner WHERE id = :id")
                        .setParameter("id", tenant.getPartnerId())
                        .getResultList();
                ref = partnerLogo.isEmpty() ? null : (String) partnerLogo.get(0);
            }
        }
        if (ref == null) {
            return null;
        }
        byte[] bytes = files.get(ref);
        String type = bytes.length > 3 && (bytes[0] & 0xff) == 0x89 ? "image/png" : "image/jpeg";
        return "data:" + type + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private JsonNode enriched(DocumentEntity doc) {
        return DocumentFileEntity.of(doc.getId(), "enriched").map(f -> {
            try {
                return json.readTree(files.get(f.getStorageKey()));
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }).orElse(json.createObjectNode());
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() || node.asText().isBlank() ? null : node.asText();
    }

    private static BigDecimal decimal(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() || node.asText().isBlank() ? BigDecimal.ZERO : new BigDecimal(node.asText());
    }

    private static String plain(JsonNode node) {
        return decimal(node).stripTrailingZeros().toPlainString();
    }

    private static String money(JsonNode node) {
        return money(decimal(node));
    }

    private static String money(BigDecimal value) {
        return String.format(java.util.Locale.ROOT, "%,.2f", value.setScale(2, RoundingMode.HALF_UP));
    }

    private static String nonZero(JsonNode node) {
        BigDecimal value = decimal(node);
        return value.signum() == 0 ? null : money(value);
    }
}

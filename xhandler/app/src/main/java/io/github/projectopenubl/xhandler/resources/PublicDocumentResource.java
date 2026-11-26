package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.utils.PublicStatus;
import io.github.projectopenubl.xhandler.utils.DocumentFiles;
import io.github.projectopenubl.xhandler.models.DocumentFileProvider;
import io.github.projectopenubl.xhandler.models.DocumentEventProvider;
import io.github.projectopenubl.xhandler.models.DocumentProvider;
import io.github.projectopenubl.xhandler.models.CompanyProvider;
import io.github.projectopenubl.xhandler.managers.AccessLinkManager;
import io.github.projectopenubl.xhandler.models.DocumentModel;

import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.models.CompanyModel;
import io.github.projectopenubl.xhandler.files.FileStore;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/**
 * Buyer access by capability link (DESIGN.md §3.3, §12): no login, rate limited at the gateway.
 * The token opens one document's data and files; downloading a file counts as delivery (§11.6).
 * Unknown and expired tokens are both 404.
 */
@Path("/api/v1/public/documents/{token}")
public class PublicDocumentResource {

    private final DocumentProvider documentProvider;

    private final DocumentFileProvider documentFileProvider;

    private final DocumentEventProvider documentEventProvider;

    private final CompanyProvider companyProvider;

    public record PublicDocument(
            String emisorRuc,
            String emisorRazonSocial,
            String tipoComprobante,
            String serieNumero,
            LocalDate fechaEmision,
            String moneda,
            BigDecimal total,
            String status,
            List<String> files,
            @org.eclipse.microprofile.openapi.annotations.media.Schema(description = "El Libro de Reclamaciones virtual del emisor, si lo configuró.")
            String libroReclamacionesUrl) {}

    private final AccessLinkManager links;
    private final TenantAccess access;
    private final FileStore files;
    private final io.github.projectopenubl.xhandler.pdf.DocumentPdf pdf;

    public PublicDocumentResource(AccessLinkManager links, TenantAccess access, FileStore files,
            io.github.projectopenubl.xhandler.pdf.DocumentPdf pdf, CompanyProvider companyProvider, DocumentEventProvider documentEventProvider, DocumentFileProvider documentFileProvider, DocumentProvider documentProvider) {
        this.documentProvider = documentProvider;
        this.documentFileProvider = documentFileProvider;
        this.documentEventProvider = documentEventProvider;
        this.companyProvider = companyProvider;
        this.pdf = pdf;
        this.links = links;
        this.access = access;
        this.files = files;
    }

    @Operation(operationId = "getPublicDocument")
    @GET
    @Transactional
    public PublicDocument get(@PathParam("token") String token) {
        DocumentModel doc = open(token);
        CompanyModel company = companyProvider.getCompany(doc.getCompanyId()).orElse(null);
        java.util.List<String> available = documentFileProvider.getDocumentFiles(doc.getId()).stream()
                .map(f -> switch (f.getKind()) {
                    case "xml_signed" -> "xml";
                    case "cdr_zip" -> "cdr";
                    default -> null;
                })
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        available.add("pdf");
        java.util.Collections.sort(available);
        // A test-environment document must never read as a real one
        String status = "production".equals(doc.getSunatEnv()) ? PublicStatus.of(doc.getStatus()) : "documento_de_prueba";
        return new PublicDocument(company.getRuc(), company.getRazonSocial(), doc.getTipoComprobante(), doc.serieNumero(), doc.getFechaEmision(),
                doc.getMoneda(), doc.getTotal(), status, available, company.getLibroReclamacionesUrl());
    }

    @APIResponse(responseCode = "200", description = "El archivo: XML firmado, CDR (ZIP) o PDF.", content = {
            @Content(mediaType = "application/xml", schema = @Schema(type = SchemaType.STRING, format = "binary")),
            @Content(mediaType = "application/zip", schema = @Schema(type = SchemaType.STRING, format = "binary")),
            @Content(mediaType = "application/pdf", schema = @Schema(type = SchemaType.STRING, format = "binary"))
    })
    @Operation(operationId = "downloadPublicDocumentFile")
    @GET
    @Path("/{kind}")
    public Response file(@PathParam("token") String token, @PathParam("kind") String kind) {
        String stored = switch (kind) {
            case "xml" -> "xml_signed";
            case "cdr" -> "cdr_zip";
            case "pdf" -> "pdf";
            default -> throw ApiException.notFound();
        };
        record Download(byte[] content, String name) {}
        Download download = QuarkusTransaction.requiringNew().call(() -> {
            DocumentModel doc = open(token);
            byte[] content = "pdf".equals(kind)
                    ? pdf.render(doc.getId(), links.url(token))
                    : files.get(documentFileProvider.getDocumentFile(doc.getId(), stored).orElseThrow(ApiException::notFound).getStorageKey());
            markDelivered(doc.getId());
            return new Download(content, DocumentFiles.fileName(null, doc, kind));
        });
        return Response.ok(download.content())
                .type(DocumentFiles.contentType(kind))
                .header("Content-Disposition", "attachment; filename=\"" + download.name() + "\"")
                .build();
    }

    private DocumentModel open(String token) {
        AccessLinkManager.Resolved resolved = links.resolve(token).orElseThrow(ApiException::notFound);
        access.enterAsPlatform(resolved.tenantId());
        DocumentModel doc = documentProvider.getDocument(resolved.documentId()).orElse(null);
        if (doc == null) {
            throw ApiException.notFound();
        }
        return doc;
    }

    /** First download by the buyer: the document counts as delivered (voiding is then refused, §9.3). */
    private void markDelivered(java.util.UUID documentId) {
        DocumentModel doc = documentProvider.getDocumentForUpdate(documentId).orElse(null);
        documentProvider.addPortalDelivery(Ids.newId(), doc.getTenantId(), doc.getId());
        if (doc.getDeliveredAt() == null) {
            doc.setDeliveredAt(Instant.now());
            doc.setDeliveredSource("service");
            doc.setUpdatedAt(doc.getDeliveredAt());
            documentEventProvider.addDocumentEvent(doc, null, "delivered (buyer portal)", "public:buyer", null);
        }
    }

}

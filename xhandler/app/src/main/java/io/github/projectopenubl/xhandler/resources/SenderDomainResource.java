package io.github.projectopenubl.xhandler.resources;

import io.github.projectopenubl.xhandler.models.EmailProvider;
import io.github.projectopenubl.xhandler.resources.exceptions.ApiException;
import io.github.projectopenubl.xhandler.utils.Ids;
import io.github.projectopenubl.xhandler.managers.AuditManager;
import io.github.projectopenubl.xhandler.security.Role;
import io.github.projectopenubl.xhandler.security.TenantAccess;
import io.github.projectopenubl.xhandler.security.TenantScope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.naming.directory.Attribute;
import javax.naming.directory.InitialDirContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

/**
 * A tenant's own sender domain (DESIGN.md §26.5): proven with a DNS TXT record
 * {@code _xhandler.<domain> TXT "xhandler-verification=<token>"}. The DKIM and SPF records for the
 * platform's mail provider come with the provider setup; verification only proves the domain is
 * the tenant's, so mail may be sent as it.
 */
@Path("/api/v1/tenants/{tenantId}/sender-domains")
public class SenderDomainResource {

    public record DomainRequest(@NotBlank @Pattern(regexp = "(?i)[a-z0-9-]+(\\.[a-z0-9-]+)+") String domain) {}

    public record DomainView(UUID id, String domain, String txtName, String txtValue, Instant verifiedAt) {}

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TenantAccess access;
    private final EmailProvider emailProvider;
    private final AuditManager auditor;

    public SenderDomainResource(TenantAccess access, EmailProvider emailProvider, AuditManager auditor) {
        this.access = access;
        this.emailProvider = emailProvider;
        this.auditor = auditor;
    }

    @APIResponse(responseCode = "201", description = "Dominio agregado (o el existente).",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DomainView.class)))
    @Operation(operationId = "addSenderDomain")
    @POST
    @Transactional
    public Response add(@PathParam("tenantId") UUID tenantId, @Valid DomainRequest request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        byte[] raw = new byte[16];
        RANDOM.nextBytes(raw);
        String domain = request.domain().toLowerCase(java.util.Locale.ROOT);
        UUID id = Ids.newId();
        emailProvider.addSenderDomain(id, tenantId, domain, HexFormat.of().formatHex(raw));
        auditor.record(scope, "sender_domain.added", "sender_domain", domain);
        return Response.status(201).entity(find(domain)).build();
    }

    @Operation(operationId = "listSenderDomains")
    @GET
    @Transactional
    public List<DomainView> list(@PathParam("tenantId") UUID tenantId) {
        access.enter(tenantId, Role.VIEWER);
        return emailProvider.getSenderDomains().stream().map(SenderDomainResource::view).toList();
    }

    @Operation(operationId = "verifySenderDomain")
    @POST
    @Path("/{domain}/verify")
    public DomainView verify(@PathParam("tenantId") UUID tenantId, @PathParam("domain") String domain) {
        DomainView pending = QuarkusTransaction.requiringNew().call(() -> {
            access.enter(tenantId, Role.ADMIN);
            return find(domain.toLowerCase(java.util.Locale.ROOT));
        });
        if (!txtRecords(pending.txtName()).contains(pending.txtValue())) {
            throw ApiException.conflict("dns_record_missing", "No se encontró el registro TXT " + pending.txtName() + " con el valor indicado.");
        }
        return QuarkusTransaction.requiringNew().call(() -> {
            TenantScope scope = access.enter(tenantId, Role.ADMIN);
            emailProvider.markSenderDomainVerified(pending.domain());
            auditor.record(scope, "sender_domain.verified", "sender_domain", pending.domain());
            return find(pending.domain());
        });
    }

    static List<String> txtRecords(String name) {
        try {
            Hashtable<String, String> env = new Hashtable<>(Map.of("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory",
                    "com.sun.jndi.dns.timeout.initial", "3000", "com.sun.jndi.dns.timeout.retries", "2"));
            Attribute txt = new InitialDirContext(env).getAttributes(name, new String[] {"TXT"}).get("TXT");
            if (txt == null) {
                return List.of();
            }
            List<String> values = new java.util.ArrayList<>();
            for (int i = 0; i < txt.size(); i++) {
                values.add(txt.get(i).toString().replace("\"", ""));
            }
            return values;
        } catch (javax.naming.NamingException e) {
            return List.of();
        }
    }

    private DomainView find(String domain) {
        return emailProvider.getSenderDomain(domain).map(SenderDomainResource::view).orElseThrow(ApiException::notFound);
    }

    static DomainView view(EmailProvider.SenderDomain r) {
        String domain = r.domain();
        return new DomainView(r.id(), domain, "_xhandler." + domain, "xhandler-verification=" + r.verificationToken(), r.verifiedAt());
    }
}

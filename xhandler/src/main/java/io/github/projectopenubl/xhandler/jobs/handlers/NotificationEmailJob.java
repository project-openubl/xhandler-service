package io.github.projectopenubl.xhandler.jobs.handlers;

import io.github.projectopenubl.xhandler.mail.EmailSender;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;

/** Emails one tenant notification through the platform sender. */
@ApplicationScoped
public class NotificationEmailJob implements JobHandler {

    public static final String NAME = "notification.email";

    private final EmailSender sender;
    private final io.github.projectopenubl.xhandler.security.TenantAccess access;
    private final io.github.projectopenubl.xhandler.models.EmailProvider emailProvider;

    public NotificationEmailJob(EmailSender sender, io.github.projectopenubl.xhandler.security.TenantAccess access,
            io.github.projectopenubl.xhandler.models.EmailProvider emailProvider) {
        this.sender = sender;
        this.access = access;
        this.emailProvider = emailProvider;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, Object> run(JobContext context) {
        String to = context.args().path("to").asString();
        boolean suppressed = io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() -> {
            access.enterAsPlatform(context.tenantId());
            return emailProvider.isEmailSuppressed(to);
        });
        if (suppressed) {
            return Map.of("skipped", to);
        }
        String title = context.args().path("title").asString();
        String body = context.args().path("body").asString();
        String html = "<p>" + escape(body) + "</p><p style=\"color:#666\">Notificación de xhandler. Configure sus avisos en la consola.</p>";
        sender.sendPlatform(new EmailSender.Message(null, null, List.of(to), List.of(), title, html, List.of()));
        return Map.of("to", to);
    }

    static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

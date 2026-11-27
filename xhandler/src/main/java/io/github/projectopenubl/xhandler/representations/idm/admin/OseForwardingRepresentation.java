package io.github.projectopenubl.xhandler.representations.idm.admin;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** The OSE forwarding queue against SUNAT's 1-hour limit. */
@Schema(name = "OseForwarding")
public record OseForwardingRepresentation(long pending, double oldestPendingSeconds, long rejected, long blocked, long late, long forwarded, List<OseRejectedForwardRepresentation> rejectedItems) {}

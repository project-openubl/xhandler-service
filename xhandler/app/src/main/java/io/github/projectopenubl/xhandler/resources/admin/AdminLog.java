package io.github.projectopenubl.xhandler.resources.admin;

import org.jboss.logging.Logger;

/** The platform audit log category shared by every admin resource. */
public final class AdminLog {

    public static final Logger PLATFORM_AUDIT = Logger.getLogger("xhandler.audit.platform");

    private AdminLog() {}
}

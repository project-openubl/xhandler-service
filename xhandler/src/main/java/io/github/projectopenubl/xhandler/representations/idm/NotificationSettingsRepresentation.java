package io.github.projectopenubl.xhandler.representations.idm;

import jakarta.validation.constraints.Email;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "NotificationSettings")
public record NotificationSettingsRepresentation(@Email String notificationEmail) {}

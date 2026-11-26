package io.github.projectopenubl.xhandler.models;

import java.util.UUID;

/** A domain object, independent of how it is stored. */
public interface Model {

    UUID getId();

    ModelType getModelType();
}

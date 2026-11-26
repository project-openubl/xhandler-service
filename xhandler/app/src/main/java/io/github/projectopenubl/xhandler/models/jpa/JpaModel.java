package io.github.projectopenubl.xhandler.models.jpa;

/** A model backed by a JPA entity. */
public interface JpaModel<T> {

    T getEntity();
}

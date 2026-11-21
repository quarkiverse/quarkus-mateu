package io.quarkiverse.mateu.it;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import io.mateu.uidl.annotations.EditableOnlyWhenCreating;
import io.mateu.uidl.interfaces.Identifiable;

/**
 * A record with an enum (the id is the user's to type when creating, hence
 * {@code @EditableOnlyWhenCreating}; without it Mateu treats {@code id} as a technical id the store
 * assigns): what a native image must reach reflectively from a CRUD's generic argument.
 */
public record Product(@EditableOnlyWhenCreating @NotEmpty String id, @NotEmpty String name, double price,
        @NotNull ProductStatus status)
        implements
            Identifiable {
}

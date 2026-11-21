package io.quarkiverse.mateu.it;

import jakarta.inject.Inject;

import io.mateu.core.infra.declarative.orchestrators.crud.AutoCrud;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.UI;
import io.mateu.uidl.interfaces.CrudStore;

/**
 * A full CRUD over a record. In a native image its listing, forms and saves exercise what the
 * extension registers: the record and its enum (reached through the generic argument only), the
 * reflective instantiation of the entity from the form's state, the validation messages.
 */
@UI("/products")
@Title("Products")
public class Products extends AutoCrud<Product> {

    @Inject
    ProductStore store;

    @Override
    public CrudStore<Product> store() {
        return store;
    }
}

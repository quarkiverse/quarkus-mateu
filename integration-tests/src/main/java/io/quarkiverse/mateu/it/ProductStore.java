package io.quarkiverse.mateu.it;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

import io.mateu.uidl.interfaces.CrudStore;

/** An ordinary CDI bean holding the products; injected into the CRUD, which Mateu instantiates. */
@ApplicationScoped
public class ProductStore implements CrudStore<Product> {

    private final Map<String, Product> products = new ConcurrentHashMap<>();

    public ProductStore() {
        save(new Product("P-001", "Espresso machine", 249.0, ProductStatus.Available));
        save(new Product("P-002", "Milk frother", 39.9, ProductStatus.OutOfStock));
    }

    @Override
    public Optional<Product> findById(String id) {
        return Optional.ofNullable(products.get(id));
    }

    @Override
    public String save(Product product) {
        products.put(product.id(), product);
        return product.id();
    }

    @Override
    public List<Product> findAll() {
        return products.values().stream().sorted(Comparator.comparing(Product::id)).toList();
    }

    @Override
    public void deleteAllById(List<String> ids) {
        ids.forEach(products::remove);
    }
}

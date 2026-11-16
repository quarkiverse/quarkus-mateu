package io.quarkiverse.mateu.it;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * An ordinary application service. Only the view model injects it, and Mateu instantiates the
 * view model itself, so ArC sees no injection point: the extension is what keeps this bean.
 */
@ApplicationScoped
public class Greetings {

    public String greet(String name) {
        return "Hello, " + (name == null || name.isBlank() ? "stranger" : name) + "!";
    }
}

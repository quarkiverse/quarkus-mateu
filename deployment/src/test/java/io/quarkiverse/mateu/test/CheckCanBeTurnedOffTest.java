package io.quarkiverse.mateu.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.mateu.uidl.annotations.UI;
import io.quarkus.test.QuarkusExtensionTest;

/** {@code quarkus.mateu.verify-generated-controllers=false}: the app starts (and the UI 404s). */
public class CheckCanBeTurnedOffTest {

    @UI("/unchecked")
    public static class UncheckedUi {
        String name;
    }

    @RegisterExtension
    static final QuarkusExtensionTest app = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClasses(UncheckedUi.class))
            .overrideConfigKey("quarkus.mateu.verify-generated-controllers", "false");

    @Test
    public void theAppStarts() {
        assertTrue(true);
    }
}

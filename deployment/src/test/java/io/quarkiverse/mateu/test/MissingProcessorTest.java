package io.quarkiverse.mateu.test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.mateu.uidl.annotations.UI;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * This module's tests are compiled WITHOUT the Mateu annotation processor, so the {@code @UI}
 * below has no generated controller: the build must fail and say how to fix it.
 */
public class MissingProcessorTest {

    @UI("/unprocessed")
    public static class UnprocessedUi {
        String name;
    }

    @RegisterExtension
    static final QuarkusExtensionTest app = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClasses(UnprocessedUi.class))
            .assertException(t -> {
                Throwable root = t;
                while (root.getCause() != null && !(root instanceof IllegalStateException)) {
                    root = root.getCause();
                }
                String message = String.valueOf(root.getMessage());
                assertTrue(message.contains("no controller was generated for " + UnprocessedUi.class.getName()),
                        message);
                assertTrue(message.contains("mateu-annotation-processor-quarkus"), message);
            });

    @Test
    public void theBuildFails() {
        fail("the build should have failed");
    }
}

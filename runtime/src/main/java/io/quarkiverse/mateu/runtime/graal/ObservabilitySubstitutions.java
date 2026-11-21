package io.quarkiverse.mateu.runtime.graal;

import java.util.function.BooleanSupplier;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * Mateu's observability reaches OpenTelemetry through one class only ({@code OtelTelemetry}), and
 * only when the API is on the classpath: a JVM app without it never loads that class. A native image
 * links every method the code can reach, though, and fails on the missing OpenTelemetry types. In an
 * app without OpenTelemetry, {@code Observability.telemetry()} is replaced by what it returns there
 * at run time anyway: no telemetry. With OpenTelemetry present (quarkus-opentelemetry), nothing is
 * substituted.
 */
@TargetClass(className = "io.mateu.core.infra.observability.Observability", onlyWith = ObservabilitySubstitutions.OpenTelemetryAbsent.class)
final class ObservabilitySubstitutions {

    @Substitute
    public static io.mateu.core.infra.observability.Telemetry telemetry() {
        return io.mateu.core.infra.observability.Telemetry.NOOP;
    }

    static final class OpenTelemetryAbsent implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            try {
                Class.forName("io.opentelemetry.context.ImplicitContextKeyed", false,
                        Thread.currentThread().getContextClassLoader());
                return false;
            } catch (ClassNotFoundException | LinkageError e) {
                return true;
            }
        }
    }
}

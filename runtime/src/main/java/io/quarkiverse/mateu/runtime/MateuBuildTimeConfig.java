package io.quarkiverse.mateu.runtime;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Build-time settings of the Mateu extension.
 *
 * Mateu's own runtime settings keep their {@code mateu.*} names (for example
 * {@code mateu.cors.allowed-origins} or {@code mateu.mcp.enabled}): they are read from the Quarkus
 * configuration by the adapter, exactly as in every other Mateu runtime.
 */
@ConfigMapping(prefix = "quarkus.mateu")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface MateuBuildTimeConfig {

    /**
     * Fail the build when a {@code @UI} class of the application has no generated controller,
     * which means the Mateu annotation processor
     * ({@code io.mateu:mateu-annotation-processor-quarkus}) did not run. Without it every route of
     * that UI answers 404.
     */
    @WithDefault("true")
    boolean verifyGeneratedControllers();

    /**
     * Keep the beans that Mateu injects into the view models it instantiates itself.
     *
     * Mateu creates a routed view model per request and fills its {@code @Inject} fields from the
     * container, so ArC sees no injection point for those services and would remove them as
     * unused. When enabled, the extension marks the types of those injection points, and every
     * bean implementing a Mateu interface ({@code CrudStore}, {@code ListingExporter},
     * {@code PrincipalResolver}...), as unremovable.
     */
    @WithDefault("true")
    boolean keepInjectedBeans();
}

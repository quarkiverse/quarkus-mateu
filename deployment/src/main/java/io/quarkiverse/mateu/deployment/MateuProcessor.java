package io.quarkiverse.mateu.deployment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import io.quarkiverse.mateu.runtime.MateuBuildTimeConfig;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem.ValidationErrorBuildItem;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ApplicationIndexBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceDirectoryBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

class MateuProcessor {

    private static final Logger LOG = Logger.getLogger(MateuProcessor.class);

    private static final String FEATURE = "mateu";

    static final DotName UI = DotName.createSimple("io.mateu.uidl.annotations.UI");
    private static final DotName INJECT = DotName.createSimple("jakarta.inject.Inject");
    private static final DotName INSTANCE = DotName.createSimple("jakarta.enterprise.inject.Instance");
    private static final DotName PROVIDER = DotName.createSimple("jakarta.inject.Provider");

    /** The suffix of the JAX-RS resource the annotation processor generates for each {@code @UI}. */
    static final String GENERATED_CONTROLLER_SUFFIX = "MateuController";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * mateu-core and mateu-quarkus ship a Jandex index (their beans are discovered as they are).
     * The UIDL annotations and the wire DTOs do not carry beans but are indexed for the reflection
     * registration below and for the {@code @UI} lookup.
     */
    @BuildStep
    void indexMateuLibraries(BuildProducer<IndexDependencyBuildItem> index) {
        index.produce(new IndexDependencyBuildItem("io.mateu", "mateu-uidl"));
        index.produce(new IndexDependencyBuildItem("io.mateu", "mateu-dtos"));
    }

    /**
     * Mateu instantiates the routed view models itself (fresh on every request) and fills their
     * {@code @Inject} fields from the container. ArC cannot see those injection points, so it would
     * remove the services they need as unused beans. The adapter works around it today by
     * defaulting {@code quarkus.arc.remove-unused-beans=fwk}; this step keeps exactly the beans
     * that are needed instead:
     * <ul>
     * <li>the types injected into application classes that are not beans themselves (view models,
     * cruds, wizards...);</li>
     * <li>every bean implementing a Mateu type, which Mateu looks up programmatically
     * ({@code CrudStore}, {@code ListingExporter}, {@code ComponentAdapter}, {@code PrincipalResolver},
     * {@code FileStorage}...).</li>
     * </ul>
     */
    @BuildStep
    void keepBeansMateuInjects(MateuBuildTimeConfig config, ApplicationIndexBuildItem applicationIndex,
            BuildProducer<UnremovableBeanBuildItem> unremovable) {
        if (!config.keepInjectedBeans()) {
            return;
        }
        Set<DotName> injected = injectedIntoNonBeans(applicationIndex.getIndex());
        if (!injected.isEmpty()) {
            LOG.debugf("Mateu keeps the beans injected into its view models: %s", injected);
            unremovable.produce(UnremovableBeanBuildItem.beanTypes(injected));
        }
        unremovable.produce(new UnremovableBeanBuildItem(bean -> bean.getTypes().stream()
                .anyMatch(t -> t.name().toString().startsWith("io.mateu."))));
    }

    static Set<DotName> injectedIntoNonBeans(IndexView index) {
        Set<DotName> types = new HashSet<>();
        for (AnnotationInstance inject : index.getAnnotations(INJECT)) {
            AnnotationTarget target = inject.target();
            ClassInfo declaring;
            List<Type> injectedTypes = new ArrayList<>();
            switch (target.kind()) {
                case FIELD -> {
                    declaring = target.asField().declaringClass();
                    injectedTypes.add(target.asField().type());
                }
                case METHOD -> {
                    MethodInfo method = target.asMethod();
                    declaring = method.declaringClass();
                    injectedTypes.addAll(method.parameterTypes());
                }
                default -> {
                    continue;
                }
            }
            if (BuiltinScope.isDeclaredOn(declaring)) {
                // a real bean: ArC already sees its injection points
                continue;
            }
            for (Type type : injectedTypes) {
                types.add(beanTypeOf(type));
            }
        }
        return types;
    }

    private static DotName beanTypeOf(Type type) {
        if (type.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            ParameterizedType parameterized = type.asParameterizedType();
            if ((parameterized.name().equals(INSTANCE) || parameterized.name().equals(PROVIDER))
                    && !parameterized.arguments().isEmpty()) {
                return parameterized.arguments().get(0).name();
            }
        }
        return type.name();
    }

    /**
     * The one step a Mateu app still needs by hand is the annotation processor, which generates a
     * Quarkus REST resource per {@code @UI} class. When it is missing nothing fails: the app starts
     * and every route of the UI answers 404. Say so at build time instead.
     */
    @BuildStep
    void verifyGeneratedControllers(MateuBuildTimeConfig config, ApplicationIndexBuildItem applicationIndex,
            CombinedIndexBuildItem combinedIndex, BuildProducer<ValidationErrorBuildItem> errors) {
        if (!config.verifyGeneratedControllers()) {
            return;
        }
        List<String> missing = new ArrayList<>();
        for (AnnotationInstance ui : applicationIndex.getIndex().getAnnotations(UI)) {
            if (ui.target().kind() != AnnotationTarget.Kind.CLASS) {
                continue;
            }
            String uiClass = ui.target().asClass().name().toString();
            if (combinedIndex.getIndex().getClassByName(uiClass + GENERATED_CONTROLLER_SUFFIX) == null) {
                missing.add(uiClass + " (@UI(\"" + ui.value().asString() + "\"))");
            }
        }
        if (!missing.isEmpty()) {
            errors.produce(new ValidationErrorBuildItem(
                    new IllegalStateException("Mateu: no controller was generated for " + String.join(", ", missing)
                            + ". Add io.mateu:mateu-annotation-processor-quarkus to the annotationProcessorPaths of the"
                            + " maven-compiler-plugin (next to any other processor you use, such as Lombok)."
                            + " Set quarkus.mateu.verify-generated-controllers=false to skip this check.")));
        }
    }

    /**
     * Native image: the renderer assets, the specs and the wire DTOs. This is NOT enough for a
     * native Mateu app yet (Mateu maps every view model reflectively, so its fields, methods and
     * the types they reach would need registering too); it is the part that is known up front.
     */
    @BuildStep
    void nativeImage(CombinedIndexBuildItem combinedIndex, ApplicationIndexBuildItem applicationIndex,
            BuildProducer<NativeImageResourceDirectoryBuildItem> resources,
            BuildProducer<ReflectiveClassBuildItem> reflection) {
        resources.produce(new NativeImageResourceDirectoryBuildItem("static"));
        resources.produce(new NativeImageResourceDirectoryBuildItem("META-INF/mateu"));
        resources.produce(new NativeImageResourceDirectoryBuildItem("specs"));
        List<String> classes = new ArrayList<>();
        for (ClassInfo dto : combinedIndex.getIndex().getKnownClasses()) {
            String name = dto.name().toString();
            if (name.startsWith("io.mateu.dtos.")) {
                classes.add(name);
            }
        }
        for (AnnotationInstance ui : applicationIndex.getIndex().getAnnotations(UI)) {
            if (ui.target().kind() == AnnotationTarget.Kind.CLASS) {
                classes.add(ui.target().asClass().name().toString());
            }
        }
        reflection.produce(ReflectiveClassBuildItem.builder(classes.toArray(String[]::new))
                .constructors().methods().fields().build());
    }
}

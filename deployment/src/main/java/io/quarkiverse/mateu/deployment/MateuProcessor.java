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
import io.quarkus.deployment.builditem.nativeimage.NativeImageProxyDefinitionBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBundleBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourcePatternsBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;

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
     * Native image. Mateu maps every view model REFLECTIVELY at run time: it instantiates the class a
     * request names ({@code serverSideType}), walks its fields, methods, record components and
     * annotations, fills its state through Jackson and invokes its action methods — and reaches
     * further classes by name (routes.yaml view models), by generic arguments ({@code AutoCrud<T>})
     * and by field types ({@code @Menu}). A walk of the graph from each {@code @UI} would always
     * miss some, so the whole surface Mateu can reflect on is registered instead: every class of
     * the application, every class of Mateu's own artifacts ({@code io.mateu.*}: the UIDL, the
     * core's archetypes and the wire DTOs) and every class of a UI module the app depends on (a jar
     * indexed by Mateu's indexer, {@code META-INF/mateu/ui-registrations}). Applications are small;
     * the cost is a larger image, not a broken screen.
     */
    @BuildStep
    void nativeImage(CombinedIndexBuildItem combinedIndex, ApplicationIndexBuildItem applicationIndex,
            BuildProducer<NativeImageResourcePatternsBuildItem> resources,
            BuildProducer<NativeImageProxyDefinitionBuildItem> proxies,
            BuildProducer<RuntimeInitializedClassBuildItem> runtimeInit,
            BuildProducer<NativeImageResourceBundleBuildItem> bundles,
            BuildProducer<ReflectiveClassBuildItem> reflection) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        // As patterns, not NativeImageResourceDirectoryBuildItem: that one only walks a directory
        // INSIDE A JAR, and only the first one found — the app's own specs/ (a plain directory) failed
        // the build, and a second jar's static/ would be skipped. Patterns cover every classpath entry.
        resources.produce(NativeImageResourcePatternsBuildItem.builder()
                .includeGlobs("static/**", "META-INF/mateu/**", "specs/**", "io/mateu/core/infra/pwa/**")
                .build());
        // the validation messages Mateu shows next to a field (ValidationLocale reads Hibernate
        // Validator's bundles for the request's language)
        for (String bundle : List.of("org.hibernate.validator.ValidationMessages", "ValidationMessages")) {
            if (loader.getResource(bundle.replace('.', '/') + ".properties") != null) {
                bundles.produce(new NativeImageResourceBundleBuildItem(bundle));
            }
        }
        // RequestOverride wraps the request in a JDK proxy of HttpRequest
        proxies.produce(new NativeImageProxyDefinitionBuildItem("io.mateu.uidl.interfaces.HttpRequest"));

        Set<String> classes = new java.util.TreeSet<>();
        for (ClassInfo type : applicationIndex.getIndex().getKnownClasses()) {
            classes.add(type.name().toString());
        }
        for (ClassInfo type : combinedIndex.getIndex().getKnownClasses()) {
            String name = type.name().toString();
            if (name.startsWith("io.mateu.") && !name.startsWith("io.mateu.annotationprocessing.")) {
                classes.add(name);
            }
        }
        // A Mateu class whose signatures name an OPTIONAL dependency the app does not have (OpenTelemetry
        // for OtelTelemetry, …) must not be registered: native-image would link it and fail. Resolve
        // each one's members here, on the app's classpath, and leave out what does not resolve.
        List<String> resolvable = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (String name : classes) {
            if (resolves(name, loader)) {
                resolvable.add(name);
            } else {
                skipped.add(name);
            }
        }
        // Mateu's own classes keep run-time state in statics (agent sessions holding a SecureRandom,
        // caches, idempotency tables…): initialized at build time, that state would be frozen into the
        // image heap. They initialize at run time. Only the classes Mateu SHIPS (from the index) — the
        // beans ArC generates in the same packages must stay build-time initialized.
        for (String name : resolvable) {
            if (name.startsWith("io.mateu.")) {
                runtimeInit.produce(new RuntimeInitializedClassBuildItem(name));
            }
        }
        // An application class holding a Mateu object in a STATIC field (the controllers Mateu's
        // processor generates keep their IndexPage.Spec there) would put it in the image heap at build
        // time, while its class initializes at run time: it initializes at run time too.
        for (ClassInfo type : applicationIndex.getIndex().getKnownClasses()) {
            boolean holdsMateuState = type.fields().stream()
                    .anyMatch(f -> java.lang.reflect.Modifier.isStatic(f.flags())
                            && f.type().name().toString().startsWith("io.mateu."));
            if (holdsMateuState) {
                runtimeInit.produce(new RuntimeInitializedClassBuildItem(type.name().toString()));
            }
        }
        // Arrays created reflectively (Array.newInstance): commons-beanutils' ConvertUtilsBean, which
        // Mateu's type coercion uses, builds an array converter for each of these types when it
        // starts; Mateu's own coercion builds arrays of the app's types for array fields.
        List<String> arrays = new ArrayList<>(List.of("[Z", "[B", "[C", "[D", "[F", "[I", "[J", "[S"));
        for (String element : List.of("java.io.File", "java.lang.Boolean", "java.lang.Byte",
                "java.lang.Character", "java.lang.Class", "java.lang.Double", "java.lang.Float",
                "java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.String",
                "java.math.BigDecimal", "java.math.BigInteger", "java.net.URL", "java.sql.Date",
                "java.sql.Time", "java.sql.Timestamp", "java.util.Calendar", "java.util.Date")) {
            arrays.add("[L" + element + ";");
        }
        for (ClassInfo type : applicationIndex.getIndex().getKnownClasses()) {
            arrays.add("[L" + type.name().toString() + ";");
        }
        reflection.produce(ReflectiveClassBuildItem.builder(arrays.toArray(String[]::new)).build());
        reflection.produce(ReflectiveClassBuildItem.builder(resolvable.toArray(String[]::new))
                .constructors().methods().fields().build());
        LOG.debugf(
                "Mateu: %d classes registered for reflection in the native image, %d left out (optional dependencies absent): %s",
                resolvable.size(), skipped.size(), skipped);
    }

    private static boolean resolves(String name, ClassLoader loader) {
        try {
            Class<?> type = Class.forName(name, false, loader);
            type.getDeclaredFields();
            type.getDeclaredMethods();
            type.getDeclaredConstructors();
            return true;
        } catch (LinkageError | ClassNotFoundException | TypeNotPresentException e) {
            return false;
        }
    }
}

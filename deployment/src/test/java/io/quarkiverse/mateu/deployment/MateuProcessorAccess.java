package io.quarkiverse.mateu.deployment;

import java.util.Set;

import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;

/** Test access to the package-private rule of {@link MateuProcessor}. */
public final class MateuProcessorAccess {

    private MateuProcessorAccess() {
    }

    public static Set<DotName> injectedIntoNonBeans(IndexView index) {
        return MateuProcessor.injectedIntoNonBeans(index);
    }
}

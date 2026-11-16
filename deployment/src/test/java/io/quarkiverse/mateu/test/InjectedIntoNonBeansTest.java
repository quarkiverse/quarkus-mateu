package io.quarkiverse.mateu.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.junit.jupiter.api.Test;

import io.quarkiverse.mateu.deployment.MateuProcessorAccess;

public class InjectedIntoNonBeansTest {

    interface Store {
    }

    interface Clock {
    }

    interface Mailer {
    }

    interface Audit {
    }

    /** A view model: Mateu builds it, ArC never sees its injection points. */
    static class ViewModel {
        @Inject
        Store store;

        @Inject
        Instance<Clock> clock;

        @Inject
        void setMailer(Mailer mailer) {
        }
    }

    /** A real bean: ArC already sees what it injects. */
    @ApplicationScoped
    static class Service {
        @Inject
        Audit audit;
    }

    @Test
    public void keepsWhatIsInjectedIntoClassesThatAreNotBeans() throws Exception {
        Index index = Index.of(ViewModel.class, Service.class);
        assertEquals(Set.of(DotName.createSimple(Store.class), DotName.createSimple(Clock.class),
                DotName.createSimple(Mailer.class)), MateuProcessorAccess.injectedIntoNonBeans(index));
    }
}

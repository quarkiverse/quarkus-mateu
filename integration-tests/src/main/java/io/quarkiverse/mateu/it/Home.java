package io.quarkiverse.mateu.it;

import jakarta.inject.Inject;

import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.UI;
import io.mateu.uidl.data.Message;

/** The whole UI of the test app: a form with one field and one action, mounted at the root. */
@UI("")
@Title("Quarkus Mateu")
public class Home {

    @Inject
    Greetings greetings;

    String name;

    @Action
    Message greet() {
        return Message.success(greetings.greet(name));
    }
}

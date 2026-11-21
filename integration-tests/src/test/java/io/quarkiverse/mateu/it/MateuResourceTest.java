package io.quarkiverse.mateu.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
public class MateuResourceTest {

    private static String rq(String actionId, String componentState) {
        return """
                {"route": "", "consumedRoute": "_empty", "actionId": "%s",
                 "componentState": %s, "parameters": {}, "appState": {}}
                """.formatted(actionId, componentState);
    }

    @Test
    public void servesTheIndexPage() {
        given().accept("text/html")
                .when().get("/")
                .then()
                .statusCode(200)
                .contentType(startsWith("text/html"))
                .body(containsString("<title>Home</title>"))
                .body(containsString("<mateu-ui"));
    }

    @Test
    public void aDeepLinkAlsoGetsTheIndexPage() {
        given().accept("text/html")
                .when().get("/some/deep/link")
                .then()
                .statusCode(200)
                .body(containsString("<mateu-ui"));
    }

    @Test
    public void servesTheRendererAssets() {
        String page = given().accept("text/html").when().get("/").then().statusCode(200).extract().asString();
        var matcher = java.util.regex.Pattern.compile("src=\"(/assets/[^\"]+\\.js)\"").matcher(page);
        org.junit.jupiter.api.Assertions.assertTrue(matcher.find(), "the page loads the renderer from /assets: " + page);
        given().when().get(matcher.group(1))
                .then()
                .statusCode(200)
                .contentType(containsString("javascript"));
    }

    @Test
    public void loadsTheUiThroughTheSyncEndpoint() {
        given().contentType(ContentType.JSON)
                .body(rq("", "{}"))
                .when().post("/mateu/v3/sync/_no_route")
                .then()
                .statusCode(200)
                .contentType(startsWith("application/json"))
                .body(containsString("\"fragments\""))
                .body(containsString("Quarkus Mateu"))
                .body(containsString("\"name\""));
    }

    @Test
    public void runsAnActionWithAnInjectedServiceEvenWhenUnusedBeansAreRemoved() {
        given().contentType(ContentType.JSON)
                .body(rq("greet", "{\"name\": \"Ana\"}"))
                .when().post("/mateu/v3/sync/_no_route")
                .then()
                .statusCode(200)
                .body(containsString("Hello, Ana!"));
    }

    @Test
    public void refusesAnActionTheViewDoesNotHave() {
        given().contentType(ContentType.JSON)
                .body(rq("doesNotExist", "{}"))
                .when().post("/mateu/v3/sync/_no_route")
                .then()
                .statusCode(403);
    }

    private static String crud(String route, String actionId, String componentState) {
        return """
                {"serverSideType": "io.quarkiverse.mateu.it.Products", "route": "%s", "consumedRoute": "",
                 "actionId": "%s", "componentState": %s, "parameters": {}, "appState": {}}
                """.formatted(route, actionId, componentState);
    }

    @Test
    public void listsTheRecordsOfACrud() {
        given().contentType(ContentType.JSON)
                .body(crud("", "search", "{}"))
                .when().post("/products/mateu/v3/sync/")
                .then()
                .statusCode(200)
                .body(containsString("Espresso machine"))
                .body(containsString("OutOfStock"));
    }

    @Test
    public void createsARecordFromTheFormState() {
        given().contentType(ContentType.JSON)
                .body(crud("/new", "create",
                        "{\"id\": \"P-077\", \"name\": \"Moka pot\", \"price\": 25, \"status\": \"Available\"}"))
                .when().post("/products/mateu/v3/sync/new")
                .then()
                .statusCode(200);
        given().contentType(ContentType.JSON)
                .body(crud("", "search", "{}"))
                .when().post("/products/mateu/v3/sync/")
                .then()
                .statusCode(200)
                .body(containsString("Moka pot"));
    }

    @Test
    public void refusesACreateWithEmptyRequiredFieldsWithItsMessage() {
        given().contentType(ContentType.JSON)
                .body(crud("/new", "create", "{\"price\": 0}"))
                .when().post("/products/mateu/v3/sync/new")
                .then()
                .body(containsString("There are validation errors"));
    }

    @Test
    public void servesAScreenDeclaredInYaml() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"route": "/catalogue", "consumedRoute": "", "actionId": "",
                         "componentState": {}, "parameters": {}, "appState": {}}
                        """)
                .when().post("/yaml/mateu/v3/sync/catalogue")
                .then()
                .statusCode(200)
                .body(containsString("Catalogue from YAML"));
    }
}

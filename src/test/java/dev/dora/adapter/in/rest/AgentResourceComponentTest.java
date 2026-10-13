package dev.dora.adapter.in.rest;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Component tests for the chat REST resource, exercised in-process with
 * Rest Assured so they run under the same JVM as the unit tests and are
 * covered by the Jacoco agent.
 *
 * @author Rodrigo Prestes Machado
 */
@QuarkusTest
class AgentResourceComponentTest {

    /**
     * Ensures the application exposes the chat resource (GET is not allowed).
     */
    @Test
    void chatEndpointIsRegistered() {
        given()
                .when().get("/dora/chat")
                .then()
                .statusCode(405);
    }

}

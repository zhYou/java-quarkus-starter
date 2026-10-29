package io.github.zhyou.starter.tests.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.zhyou.starter.testing.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(value = PostgresResource.class, restrictToAnnotatedClass = true)
class OrderResourceTest {
  @Test
  void lifecycleAndCustomerIsolation() {
    String base = "/api/v1/customers/" + UUID.randomUUID() + "/orders";
    var created =
        given()
            .contentType("application/json")
            .body(Map.of("description", "http order"))
            .post(base)
            .then()
            .statusCode(201)
            .header("Location", containsString(base))
            .header("X-Request-ID", not(blankOrNullString()))
            .body("status", is("NEW"))
            .extract()
            .response();
    String id = created.path("id");
    given().get(base + "/" + id).then().statusCode(200).body("description", is("http order"));
    given().get(base).then().statusCode(200).body("size()", is(1));
    given()
        .get("/api/v1/customers/" + UUID.randomUUID() + "/orders/" + id)
        .then()
        .statusCode(404)
        .body("code", is("ORDER_NOT_FOUND"));
    given()
        .post(base + "/" + id + "/complete")
        .then()
        .statusCode(200)
        .body("status", is("COMPLETED"));
    given()
        .post(base + "/" + id + "/complete")
        .then()
        .statusCode(409)
        .contentType("application/problem+json")
        .body("code", is("ORDER_CONFLICT"));
  }

  @Test
  void invalidInputsAreSafeProblems() {
    String base = "/api/v1/customers/" + UUID.randomUUID() + "/orders";
    for (String body : new String[] {"{}", "{\"description\":\" \"}", "{", "null"}) {
      var response =
          given()
              .contentType("application/json")
              .body(body)
              .post(base)
              .then()
              .statusCode(400)
              .contentType("application/problem+json")
              .body("code", is("INVALID_INPUT"))
              .body("requestId", not(blankOrNullString()))
              .extract()
              .asString();
      assertFalse(response.contains("Exception"));
      assertFalse(response.contains("jdbc:"));
    }
    given().get(base + "?limit=101").then().statusCode(400);
    given().get(base + "?limit=nope").then().statusCode(400);
    given().get("/api/v1/customers/bad/orders").then().statusCode(400);
  }

  @Test
  void concurrentHttpCompletionHasOneWinner() throws Exception {
    String base = "/api/v1/customers/" + UUID.randomUUID() + "/orders";
    String id =
        given()
            .contentType("application/json")
            .body(Map.of("description", "race"))
            .post(base)
            .then()
            .statusCode(201)
            .extract()
            .path("id");
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var calls = new ArrayList<Future<Integer>>();
      for (int i = 0; i < 4; i++)
        calls.add(pool.submit(() -> given().post(base + "/" + id + "/complete").statusCode()));
      int wins = 0;
      for (var call : calls) {
        int status = call.get(20, TimeUnit.SECONDS);
        if (status == 200) wins++;
        else assertEquals(409, status);
      }
      assertEquals(1, wins);
    }
  }

  @Test
  void validatesDescriptionLengthAfterNormalization() {
    String base = "/api/v1/customers/" + UUID.randomUUID() + "/orders";
    String exact = "x".repeat(200);
    given()
        .contentType("application/json")
        .body(Map.of("description", " " + exact + " "))
        .post(base)
        .then()
        .statusCode(201)
        .body("description", is(exact));
    given()
        .contentType("application/json")
        .body(Map.of("description", " " + exact + "x "))
        .post(base)
        .then()
        .statusCode(400)
        .contentType("application/problem+json")
        .body("code", is("INVALID_INPUT"));
  }

  @Test
  void methodAndMediaErrorsPreserveHttpSemantics() {
    String base = "/api/v1/customers/" + UUID.randomUUID() + "/orders";
    given()
        .put(base)
        .then()
        .statusCode(405)
        .header("Allow", containsString("GET"))
        .contentType("application/problem+json")
        .body("title", is("Method Not Allowed"));
    given()
        .put(base + "/" + UUID.randomUUID())
        .then()
        .statusCode(405)
        .header("Allow", containsString("GET"))
        .header("Allow", not(containsString("POST")));
    given()
        .get(base + "/" + UUID.randomUUID() + "/complete")
        .then()
        .statusCode(405)
        .header("Allow", containsString("POST"))
        .header("Allow", not(containsString("GET")));
    given()
        .put("/web/demo")
        .then()
        .statusCode(405)
        .header("Allow", containsString("GET"))
        .header("Allow", not(containsString("POST")));
    given()
        .contentType("text/plain")
        .body("wrong media type")
        .post(base)
        .then()
        .statusCode(415)
        .contentType("application/problem+json")
        .body("title", is("Unsupported Media Type"));
  }
}

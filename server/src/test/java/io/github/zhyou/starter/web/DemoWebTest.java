package io.github.zhyou.starter.tests.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.github.zhyou.starter.testing.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(value = PostgresResource.class, restrictToAnnotatedClass = true)
class DemoWebTest {
  @Test
  void htmlAndFixedRedirect() {
    given()
        .get("/web/demo")
        .then()
        .statusCode(200)
        .contentType("text/html")
        .body(containsString("Quarkus"));
    given()
        .redirects()
        .follow(false)
        .get("/web/redirect?target=https://example.com")
        .then()
        .statusCode(303)
        .header("Location", endsWith("/web/demo"));
  }
}

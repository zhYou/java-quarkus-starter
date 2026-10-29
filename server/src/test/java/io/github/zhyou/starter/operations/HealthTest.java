package io.github.zhyou.starter.tests.operations;

import static io.restassured.RestAssured.given;

import io.github.zhyou.starter.testing.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(value = PostgresResource.class, restrictToAnnotatedClass = true)
class HealthTest {
  @Test
  void internalHealthAndMetrics() {
    given().port(9001).get("/q/health/live").then().statusCode(200);
    given().port(9001).get("/q/health/ready").then().statusCode(200);
    given().port(9001).get("/q/metrics").then().statusCode(200);
    given().get("/q/health").then().statusCode(404);
  }
}

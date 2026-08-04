package com.fulfilment.application.monolith;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** Confirms the MicroProfile Health endpoints (liveness/readiness) are wired up and healthy. */
@QuarkusTest
public class HealthCheckTest {

  @Test
  public void testOverallHealthIsUp() {
    given().when().get("/q/health").then().statusCode(200).body("status", equalTo("UP"));
  }

  @Test
  public void testLivenessIsUp() {
    given().when().get("/q/health/live").then().statusCode(200).body("status", equalTo("UP"));
  }

  @Test
  public void testReadinessIsUp() {
    given().when().get("/q/health/ready").then().statusCode(200).body("status", equalTo("UP"));
  }
}

package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.fulfilment.application.monolith.warehouses.adapters.database.DbWarehouse;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

/**
 * REST-layer tests for the Warehouse endpoints: response shaping and the error paths
 * (400/404) at the HTTP boundary that the domain-level use case tests don't exercise directly.
 */
@QuarkusTest
public class WarehouseEndpointTest {

  @Inject EntityManager em;

  @Test
  public void testListAllWarehouseUnits() {
    given().when().get("warehouse").then().statusCode(200);
  }

  @Test
  public void testGetWarehouseUnitNotFound() {
    given().when().get("warehouse/DOES-NOT-EXIST").then().statusCode(404);
  }

  @Test
  public void testCreateWarehouseSuccess() {
    String code = "REST-CREATE-" + System.nanoTime();

    given()
        .contentType("application/json")
        .body(
            "{\"businessUnitCode\": \""
                + code
                + "\", \"location\": \"AMSTERDAM-001\", \"capacity\": 20, \"stock\": 5}")
        .when()
        .post("warehouse")
        .then()
        .statusCode(200)
        .body("businessUnitCode", equalTo(code))
        .body("location", equalTo("AMSTERDAM-001"));

    given().when().get("warehouse/" + code).then().statusCode(200);
  }

  @Test
  public void testCreateWarehouseDuplicateCodeReturns400() {
    String code = "REST-DUPLICATE-" + System.nanoTime();
    String body =
        "{\"businessUnitCode\": \""
            + code
            + "\", \"location\": \"AMSTERDAM-001\", \"capacity\": 20, \"stock\": 5}";

    given().contentType("application/json").body(body).when().post("warehouse").then().statusCode(200);

    given().contentType("application/json").body(body).when().post("warehouse").then().statusCode(400);
  }

  @Test
  public void testCreateWarehouseInvalidLocationReturns400() {
    given()
        .contentType("application/json")
        .body(
            "{\"businessUnitCode\": \"REST-BADLOC-"
                + System.nanoTime()
                + "\", \"location\": \"NOT-A-REAL-LOCATION\", \"capacity\": 20, \"stock\": 5}")
        .when()
        .post("warehouse")
        .then()
        .statusCode(400);
  }

  @Test
  public void testArchiveWarehouseNotFoundReturns404() {
    given().when().delete("warehouse/DOES-NOT-EXIST").then().statusCode(404);
  }

  @Test
  public void testArchiveWarehouseSuccessAndDoubleArchiveReturns400() {
    String code = "REST-ARCHIVE-" + System.nanoTime();
    createWarehouseDirect(code, "AMSTERDAM-001", 20, 5);

    given().when().delete("warehouse/" + code).then().statusCode(204);

    // already archived
    given().when().delete("warehouse/" + code).then().statusCode(400);
  }

  @Test
  public void testReplaceWarehouseNotFoundReturns400() {
    given()
        .contentType("application/json")
        .body("{\"location\": \"AMSTERDAM-001\", \"capacity\": 20, \"stock\": 5}")
        .when()
        .post("warehouse/DOES-NOT-EXIST/replacement")
        .then()
        .statusCode(400);
  }

  @Test
  public void testReplaceWarehouseSuccess() {
    String code = "REST-REPLACE-" + System.nanoTime();
    createWarehouseDirect(code, "AMSTERDAM-001", 20, 5);

    given()
        .contentType("application/json")
        .body("{\"location\": \"ZWOLLE-001\", \"capacity\": 30, \"stock\": 10}")
        .when()
        .post("warehouse/" + code + "/replacement")
        .then()
        .statusCode(200)
        .body("location", equalTo("ZWOLLE-001"))
        .body("capacity", equalTo(30));
  }

  @Test
  public void testReplaceArchivedWarehouseReturns400() {
    String code = "REST-REPLACE-ARCHIVED-" + System.nanoTime();
    createWarehouseDirect(code, "AMSTERDAM-001", 20, 5);
    given().when().delete("warehouse/" + code).then().statusCode(204);

    given()
        .contentType("application/json")
        .body("{\"location\": \"ZWOLLE-001\", \"capacity\": 30, \"stock\": 10}")
        .when()
        .post("warehouse/" + code + "/replacement")
        .then()
        .statusCode(400);
  }

  @Test
  public void testSearchWithNegativePageReturns400() {
    given().queryParam("page", -1).when().get("warehouse/search").then().statusCode(400);
  }

  @Transactional
  void createWarehouseDirect(String code, String location, int capacity, int stock) {
    DbWarehouse w = new DbWarehouse();
    w.businessUnitCode = code;
    w.location = location;
    w.capacity = capacity;
    w.stock = stock;
    w.createdAt = java.time.LocalDateTime.now();
    em.persist(w);
  }
}

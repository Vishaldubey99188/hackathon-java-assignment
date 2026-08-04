package com.fulfilment.application.monolith.stores;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * REST-layer tests for the Store CRUD endpoints: positive paths and the validation/not-found
 * error paths (422, 404).
 */
@QuarkusTest
public class StoreEndpointTest {

  @Test
  public void testListStores() {
    given().when().get("store").then().statusCode(200);
  }

  @Test
  public void testGetSingleStoreNotFound() {
    given().when().get("store/999999").then().statusCode(404);
  }

  @Test
  public void testCreateStoreWithIdSetReturns422() {
    given()
        .contentType("application/json")
        .body("{\"id\": 1, \"name\": \"INVALID-STORE\"}")
        .when()
        .post("store")
        .then()
        .statusCode(422);
  }

  @Test
  public void testCreateAndGetStoreSuccess() {
    String uniqueName = "STORE-CREATE-" + System.nanoTime();

    int id =
        given()
            .contentType("application/json")
            .body("{\"name\": \"" + uniqueName + "\", \"quantityProductsInStock\": 7}")
            .when()
            .post("store")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

    given()
        .when()
        .get("store/" + id)
        .then()
        .statusCode(200)
        .body("name", equalTo(uniqueName))
        .body("quantityProductsInStock", equalTo(7));
  }

  @Test
  public void testUpdateStoreSuccess() {
    int id = createStore("STORE-UPDATE-BEFORE-" + System.nanoTime(), 1);

    given()
        .contentType("application/json")
        .body("{\"name\": \"STORE-UPDATE-AFTER\", \"quantityProductsInStock\": 42}")
        .when()
        .put("store/" + id)
        .then()
        .statusCode(200)
        .body("name", equalTo("STORE-UPDATE-AFTER"))
        .body("quantityProductsInStock", equalTo(42));
  }

  @Test
  public void testUpdateStoreMissingNameReturns422() {
    given()
        .contentType("application/json")
        .body("{\"quantityProductsInStock\": 1}")
        .when()
        .put("store/999999")
        .then()
        .statusCode(422);
  }

  @Test
  public void testUpdateStoreNotFoundReturns404() {
    given()
        .contentType("application/json")
        .body("{\"name\": \"DOES-NOT-EXIST\"}")
        .when()
        .put("store/999999")
        .then()
        .statusCode(404);
  }

  @Test
  public void testPatchStoreSuccess() {
    int id = createStore("STORE-PATCH-BEFORE-" + System.nanoTime(), 1);

    given()
        .contentType("application/json")
        .body("{\"name\": \"STORE-PATCH-AFTER\", \"quantityProductsInStock\": 15}")
        .when()
        .patch("store/" + id)
        .then()
        .statusCode(200)
        .body("name", equalTo("STORE-PATCH-AFTER"));
  }

  @Test
  public void testPatchStoreMissingNameReturns422() {
    given()
        .contentType("application/json")
        .body("{\"quantityProductsInStock\": 1}")
        .when()
        .patch("store/999999")
        .then()
        .statusCode(422);
  }

  @Test
  public void testPatchStoreNotFoundReturns404() {
    given()
        .contentType("application/json")
        .body("{\"name\": \"DOES-NOT-EXIST\"}")
        .when()
        .patch("store/999999")
        .then()
        .statusCode(404);
  }

  @Test
  public void testDeleteStoreSuccess() {
    int id = createStore("STORE-DELETE-" + System.nanoTime(), 1);

    given().when().delete("store/" + id).then().statusCode(204);
    given().when().get("store/" + id).then().statusCode(404);
  }

  @Test
  public void testDeleteStoreNotFoundReturns404() {
    given().when().delete("store/999999").then().statusCode(404);
  }

  private int createStore(String name, int quantity) {
    return given()
        .contentType("application/json")
        .body("{\"name\": \"" + name + "\", \"quantityProductsInStock\": " + quantity + "}")
        .when()
        .post("store")
        .then()
        .statusCode(201)
        .extract()
        .path("id");
  }
}

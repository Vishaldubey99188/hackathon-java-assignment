package com.fulfilment.application.monolith.products;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.core.IsNot.not;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class ProductEndpointTest {

  @Test
  public void testCrudProduct() {
    final String path = "product";

    // List all, should have all 3 products the database has initially:
    given()
        .when()
        .get(path)
        .then()
        .statusCode(200)
        .body(containsString("TONSTAD"), containsString("KALLAX"), containsString("BESTÅ"));

    // Delete the TONSTAD:
    given().when().delete(path + "/1").then().statusCode(204);

    // List all, TONSTAD should be missing now:
    given()
        .when()
        .get(path)
        .then()
        .statusCode(200)
        .body(not(containsString("TONSTAD")), containsString("KALLAX"), containsString("BESTÅ"));
  }

  @Test
  public void testGetSingleProductNotFound() {
    given().when().get("product/999999").then().statusCode(404);
  }

  @Test
  public void testCreateProductWithIdSetReturns422() {
    given()
        .contentType("application/json")
        .body("{\"id\": 1, \"name\": \"INVALID\"}")
        .when()
        .post("product")
        .then()
        .statusCode(422);
  }

  @Test
  public void testGetSingleProductSuccess() {
    int id =
        given()
            .contentType("application/json")
            .body("{\"name\": \"GET-SINGLE-PRODUCT\", \"stock\": 3}")
            .when()
            .post("product")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

    given()
        .when()
        .get("product/" + id)
        .then()
        .statusCode(200)
        .body(containsString("GET-SINGLE-PRODUCT"));
  }

  @Test
  public void testUpdateProductSuccess() {
    int id =
        given()
            .contentType("application/json")
            .body("{\"name\": \"UPDATE-PRODUCT-BEFORE\", \"stock\": 1}")
            .when()
            .post("product")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

    given()
        .contentType("application/json")
        .body("{\"name\": \"UPDATE-PRODUCT-AFTER\", \"stock\": 9}")
        .when()
        .put("product/" + id)
        .then()
        .statusCode(200)
        .body("name", org.hamcrest.Matchers.equalTo("UPDATE-PRODUCT-AFTER"))
        .body("stock", org.hamcrest.Matchers.equalTo(9));
  }

  @Test
  public void testUpdateProductMissingNameReturns422() {
    given()
        .contentType("application/json")
        .body("{\"stock\": 5}")
        .when()
        .put("product/1")
        .then()
        .statusCode(422);
  }

  @Test
  public void testUpdateProductNotFoundReturns404() {
    given()
        .contentType("application/json")
        .body("{\"name\": \"DOES-NOT-EXIST\"}")
        .when()
        .put("product/999999")
        .then()
        .statusCode(404);
  }

  @Test
  public void testDeleteProductNotFoundReturns404() {
    given().when().delete("product/999999").then().statusCode(404);
  }
}

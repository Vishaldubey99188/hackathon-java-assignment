package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fulfilment.application.monolith.warehouses.adapters.database.DbWarehouse;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the bonus GET /warehouse/search endpoint: filtering
 * (location, min/maxCapacity, AND-combined), archived exclusion, sorting, and
 * pagination.
 */
@QuarkusTest
public class WarehouseSearchEndpointTest {

  @Inject EntityManager em;

  @BeforeEach
  @Transactional
  public void setup() {
    em.createQuery("DELETE FROM DbWarehouse").executeUpdate();

    seed("SEARCH-AMS-1", "AMSTERDAM-001", 30, LocalDateTime.now().minusDays(3), null);
    seed("SEARCH-AMS-2", "AMSTERDAM-001", 60, LocalDateTime.now().minusDays(2), null);
    seed("SEARCH-AMS-3", "AMSTERDAM-001", 90, LocalDateTime.now().minusDays(1), null);
    seed("SEARCH-ZWOLLE-1", "ZWOLLE-002", 45, LocalDateTime.now(), null);
    // Archived warehouse must never appear in search results
    seed("SEARCH-ARCHIVED", "AMSTERDAM-001", 50, LocalDateTime.now(), LocalDateTime.now());
  }

  @Transactional
  void seed(String code, String location, int capacity, LocalDateTime createdAt, LocalDateTime archivedAt) {
    DbWarehouse w = new DbWarehouse();
    w.businessUnitCode = code;
    w.location = location;
    w.capacity = capacity;
    w.stock = 0;
    w.createdAt = createdAt;
    w.archivedAt = archivedAt;
    em.persist(w);
  }

  @Test
  public void testSearchWithNoFiltersExcludesArchived() {
    given()
        .when()
        .get("/warehouse/search")
        .then()
        .statusCode(200)
        .body("totalElements", equalTo(4))
        .body("items", hasSize(4));
  }

  @Test
  public void testFilterByLocation() {
    given()
        .queryParam("location", "AMSTERDAM-001")
        .when()
        .get("/warehouse/search")
        .then()
        .statusCode(200)
        .body("totalElements", equalTo(3))
        .body("items.location", everyItem(equalTo("AMSTERDAM-001")));
  }

  @Test
  public void testCombinedLocationAndCapacityFiltersUseAndLogic() {
    // AMSTERDAM-001 AND capacity >= 60 -> only SEARCH-AMS-2 (60) and SEARCH-AMS-3 (90)
    given()
        .queryParam("location", "AMSTERDAM-001")
        .queryParam("minCapacity", 60)
        .when()
        .get("/warehouse/search")
        .then()
        .statusCode(200)
        .body("totalElements", equalTo(2))
        .body("items.businessUnitCode", org.hamcrest.Matchers.containsInAnyOrder("SEARCH-AMS-2", "SEARCH-AMS-3"));
  }

  @Test
  public void testMinAndMaxCapacityRange() {
    given()
        .queryParam("minCapacity", 40)
        .queryParam("maxCapacity", 60)
        .when()
        .get("/warehouse/search")
        .then()
        .statusCode(200)
        // SEARCH-AMS-2 (60) and SEARCH-ZWOLLE-1 (45); the archived one (50) is excluded
        .body("totalElements", equalTo(2));
  }

  @Test
  public void testSortByCapacityDescending() {
    given()
        .queryParam("sortBy", "capacity")
        .queryParam("sortOrder", "desc")
        .when()
        .get("/warehouse/search")
        .then()
        .statusCode(200)
        .body("items[0].businessUnitCode", equalTo("SEARCH-AMS-3")) // capacity 90
        .body("items[1].businessUnitCode", equalTo("SEARCH-AMS-2")); // capacity 60
  }

  @Test
  public void testPagination() {
    var page0 =
        given()
            .queryParam("pageSize", 2)
            .queryParam("page", 0)
            .queryParam("sortBy", "capacity")
            .queryParam("sortOrder", "asc")
            .when()
            .get("/warehouse/search")
            .then()
            .statusCode(200)
            .body("items", hasSize(2))
            .body("totalElements", equalTo(4))
            .extract()
            .path("items.businessUnitCode");

    var page1 =
        given()
            .queryParam("pageSize", 2)
            .queryParam("page", 1)
            .queryParam("sortBy", "capacity")
            .queryParam("sortOrder", "asc")
            .when()
            .get("/warehouse/search")
            .then()
            .statusCode(200)
            .body("items", hasSize(2))
            .extract()
            .path("items.businessUnitCode");

    assertTrue(java.util.Collections.disjoint((java.util.List<?>) page0, (java.util.List<?>) page1),
        "Pages must not overlap");
  }

  @Test
  public void testPageSizeAboveMaxIsRejected() {
    given()
        .queryParam("pageSize", 101)
        .when()
        .get("/warehouse/search")
        .then()
        .statusCode(400);
  }

  @Test
  public void testAllParametersAreOptional() {
    given()
        .when()
        .get("/warehouse/search")
        .then()
        .statusCode(200)
        .body("page", equalTo(0))
        .body("pageSize", equalTo(10));
  }
}

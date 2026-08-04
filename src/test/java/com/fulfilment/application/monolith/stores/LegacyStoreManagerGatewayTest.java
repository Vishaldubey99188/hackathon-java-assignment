package com.fulfilment.application.monolith.stores;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/** Exercises the real temp-file write path in LegacyStoreManagerGateway, unmocked. */
@QuarkusTest
public class LegacyStoreManagerGatewayTest {

  @Inject LegacyStoreManagerGateway gateway;

  @Test
  public void testCreateStoreOnLegacySystemDoesNotThrow() {
    Store store = new Store("LEGACY-CREATE");
    store.quantityProductsInStock = 5;

    assertDoesNotThrow(() -> gateway.createStoreOnLegacySystem(store));
  }

  @Test
  public void testUpdateStoreOnLegacySystemDoesNotThrow() {
    Store store = new Store("LEGACY-UPDATE");
    store.quantityProductsInStock = 8;

    assertDoesNotThrow(() -> gateway.updateStoreOnLegacySystem(store));
  }
}

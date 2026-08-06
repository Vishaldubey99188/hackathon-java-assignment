package com.fulfilment.application.monolith.warehouses.adapters.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class WarehouseRepositoryTest {

  @Inject WarehouseRepository warehouseRepository;

  @Inject EntityManager em;

  @BeforeEach
  @Transactional
  public void setup() {
    em.createQuery("DELETE FROM DbWarehouse").executeUpdate();
  }

  @Test
  public void testRemoveExistingWarehouseDeletesIt() {
    Warehouse warehouse = new Warehouse();
    warehouse.businessUnitCode = "REMOVE-TEST-001";
    warehouse.location = "AMSTERDAM-001";
    warehouse.capacity = 20;
    warehouse.stock = 5;
    warehouse.createdAt = LocalDateTime.now();

    warehouseRepository.create(warehouse);
    assertTrue(warehouseRepository.getAll().stream()
        .anyMatch(w -> "REMOVE-TEST-001".equals(w.businessUnitCode)));

    warehouseRepository.remove(warehouse);

    assertNull(warehouseRepository.findByBusinessUnitCode("REMOVE-TEST-001"));
  }

  @Test
  public void testRemoveNonExistentWarehouseThrows() {
    Warehouse warehouse = new Warehouse();
    warehouse.businessUnitCode = "REMOVE-DOES-NOT-EXIST";

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> warehouseRepository.remove(warehouse));

    assertTrue(exception.getMessage().contains("does not exist"));
  }

  @Test
  public void testRemoveDoesNotAffectOtherWarehouses() {
    Warehouse toRemove = new Warehouse();
    toRemove.businessUnitCode = "REMOVE-TEST-002";
    toRemove.location = "AMSTERDAM-001";
    toRemove.capacity = 20;
    toRemove.stock = 5;
    toRemove.createdAt = LocalDateTime.now();
    warehouseRepository.create(toRemove);

    Warehouse toKeep = new Warehouse();
    toKeep.businessUnitCode = "REMOVE-TEST-KEEP";
    toKeep.location = "ZWOLLE-001";
    toKeep.capacity = 10;
    toKeep.stock = 2;
    toKeep.createdAt = LocalDateTime.now();
    warehouseRepository.create(toKeep);

    warehouseRepository.remove(toRemove);

    assertNull(warehouseRepository.findByBusinessUnitCode("REMOVE-TEST-002"));
    assertEquals("ZWOLLE-001", warehouseRepository.findByBusinessUnitCode("REMOVE-TEST-KEEP").location);
  }
}

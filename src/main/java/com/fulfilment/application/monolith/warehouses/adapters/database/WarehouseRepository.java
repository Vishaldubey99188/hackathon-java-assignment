package com.fulfilment.application.monolith.warehouses.adapters.database;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.models.WarehouseSearchPage;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class WarehouseRepository implements WarehouseStore, PanacheRepository<DbWarehouse> {

  @Override
  @Transactional
  public List<Warehouse> getAll() {
    return this.listAll().stream().map(DbWarehouse::toWarehouse).toList();
  }

  @Override
  @Transactional
  public void create(Warehouse warehouse) {
    DbWarehouse dbWarehouse = new DbWarehouse();
    dbWarehouse.businessUnitCode = warehouse.businessUnitCode;
    dbWarehouse.location = warehouse.location;
    dbWarehouse.capacity = warehouse.capacity;
    dbWarehouse.stock = warehouse.stock;
    dbWarehouse.createdAt = warehouse.createdAt;
    dbWarehouse.archivedAt = warehouse.archivedAt;
    
    this.persist(dbWarehouse);
  }

  @Override
  @Transactional
  public void update(Warehouse warehouse) {
    DbWarehouse dbWarehouse = find("businessUnitCode", warehouse.businessUnitCode).firstResult();
    if (dbWarehouse == null) {
      throw new IllegalArgumentException(
          "Warehouse with business unit code '" + warehouse.businessUnitCode + "' does not exist");
    }

    dbWarehouse.location = warehouse.location;
    dbWarehouse.capacity = warehouse.capacity;
    dbWarehouse.stock = warehouse.stock;
    dbWarehouse.archivedAt = warehouse.archivedAt;

    getEntityManager().flush();
  }

  @Override
  @Transactional
  public void remove(Warehouse warehouse) {
    DbWarehouse dbWarehouse = find("businessUnitCode", warehouse.businessUnitCode).firstResult();
    if (dbWarehouse == null) {
      throw new IllegalArgumentException(
          "Warehouse with business unit code '" + warehouse.businessUnitCode + "' does not exist");
    }

    delete(dbWarehouse);
  }

  @Override
  @Transactional
  public Warehouse findByBusinessUnitCode(String buCode) {
    DbWarehouse dbWarehouse = find("businessUnitCode", buCode).firstResult();
    return dbWarehouse != null ? dbWarehouse.toWarehouse() : null;
  }

  /**
   * Filtered, sorted, paginated search over active (non-archived) warehouses.
   * Filters are combined with AND logic; any null/blank filter is skipped.
   */
  @Transactional
  public WarehouseSearchPage search(
      String location,
      Integer minCapacity,
      Integer maxCapacity,
      String sortBy,
      String sortOrder,
      int page,
      int pageSize) {

    StringBuilder jpql = new StringBuilder("archivedAt is null");
    Map<String, Object> params = new HashMap<>();

    if (location != null && !location.isBlank()) {
      jpql.append(" and location = :location");
      params.put("location", location);
    }
    if (minCapacity != null) {
      jpql.append(" and capacity >= :minCapacity");
      params.put("minCapacity", minCapacity);
    }
    if (maxCapacity != null) {
      jpql.append(" and capacity <= :maxCapacity");
      params.put("maxCapacity", maxCapacity);
    }

    String sortField = "capacity".equalsIgnoreCase(sortBy) ? "capacity" : "createdAt";
    Sort.Direction direction =
        "desc".equalsIgnoreCase(sortOrder) ? Sort.Direction.Descending : Sort.Direction.Ascending;

    PanacheQuery<DbWarehouse> query = find(jpql.toString(), Sort.by(sortField, direction), params);
    long totalElements = query.count();
    List<Warehouse> items =
        query.page(Page.of(page, pageSize)).list().stream().map(DbWarehouse::toWarehouse).toList();

    return new WarehouseSearchPage(items, page, pageSize, totalElements);
  }
}

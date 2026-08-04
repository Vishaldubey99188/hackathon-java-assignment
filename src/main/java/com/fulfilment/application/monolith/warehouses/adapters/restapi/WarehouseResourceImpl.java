package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.domain.models.WarehouseSearchPage;
import com.fulfilment.application.monolith.warehouses.domain.ports.ArchiveWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.CreateWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.ReplaceWarehouseOperation;
import com.warehouse.api.WarehouseResource;
import com.warehouse.api.beans.Warehouse;
import com.warehouse.api.beans.WarehouseSearchResult;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.WebApplicationException;
import java.math.BigInteger;
import java.util.List;
import org.jboss.logging.Logger;

@RequestScoped
public class WarehouseResourceImpl implements WarehouseResource {

  private static final Logger LOGGER = Logger.getLogger(WarehouseResourceImpl.class.getName());

  @Inject private WarehouseRepository warehouseRepository;
  @Inject private CreateWarehouseOperation createWarehouseOperation;
  @Inject private ArchiveWarehouseOperation archiveWarehouseOperation;
  @Inject private ReplaceWarehouseOperation replaceWarehouseOperation;

  @Override
  public List<Warehouse> listAllWarehousesUnits() {
    return warehouseRepository.getAll().stream().map(this::toWarehouseResponse).toList();
  }

  @Override
  @Transactional
  public Warehouse createANewWarehouseUnit(@NotNull Warehouse data) {
    // Convert API model to domain model
    var domainWarehouse = new com.fulfilment.application.monolith.warehouses.domain.models.Warehouse();
    domainWarehouse.businessUnitCode = data.getBusinessUnitCode();
    domainWarehouse.location = data.getLocation();
    domainWarehouse.capacity = data.getCapacity();
    domainWarehouse.stock = data.getStock() != null ? data.getStock() : 0;

    try {
      // Create warehouse through use case (includes validations)
      createWarehouseOperation.create(domainWarehouse);
      LOGGER.infof("Created warehouse businessUnitCode=%s", domainWarehouse.businessUnitCode);

      // Return the created warehouse
      return toWarehouseResponse(domainWarehouse);
    } catch (IllegalArgumentException e) {
      LOGGER.debugf("Rejected warehouse creation for businessUnitCode=%s: %s",
          domainWarehouse.businessUnitCode, e.getMessage());
      throw new WebApplicationException(e.getMessage(), 400);
    }
  }

  @Override
  public Warehouse getAWarehouseUnitByID(String id) {
    // Find warehouse by business unit code
    var domainWarehouse = warehouseRepository.findByBusinessUnitCode(id);
    
    if (domainWarehouse == null) {
      throw new WebApplicationException("Warehouse with business unit code '" + id + "' not found", 404);
    }
    
    return toWarehouseResponse(domainWarehouse);
  }

  @Override
  @Transactional
  public void archiveAWarehouseUnitByID(String id) {
    // Find warehouse by business unit code
    var domainWarehouse = warehouseRepository.findByBusinessUnitCode(id);

    if (domainWarehouse == null) {
      throw new WebApplicationException("Warehouse with business unit code '" + id + "' not found", 404);
    }

    try {
      // Archive warehouse through use case (includes validations)
      archiveWarehouseOperation.archive(domainWarehouse);
      LOGGER.infof("Archived warehouse businessUnitCode=%s", id);
    } catch (IllegalArgumentException e) {
      LOGGER.debugf("Rejected archive for businessUnitCode=%s: %s", id, e.getMessage());
      throw new WebApplicationException(e.getMessage(), 400);
    }
  }

  @Override
  @Transactional
  public Warehouse replaceTheCurrentActiveWarehouse(
      String businessUnitCode, @NotNull Warehouse data) {
    // Convert API model to domain model
    var domainWarehouse = new com.fulfilment.application.monolith.warehouses.domain.models.Warehouse();
    domainWarehouse.businessUnitCode = businessUnitCode; // Use businessUnitCode from path
    domainWarehouse.location = data.getLocation();
    domainWarehouse.capacity = data.getCapacity();
    domainWarehouse.stock = data.getStock() != null ? data.getStock() : 0;

    try {
      // Replace warehouse through use case (includes validations)
      replaceWarehouseOperation.replace(domainWarehouse);
      LOGGER.infof("Replaced warehouse businessUnitCode=%s", businessUnitCode);

      // Return the updated warehouse
      var updated = warehouseRepository.findByBusinessUnitCode(businessUnitCode);
      return toWarehouseResponse(updated);
    } catch (IllegalArgumentException e) {
      LOGGER.debugf("Rejected replace for businessUnitCode=%s: %s", businessUnitCode, e.getMessage());
      throw new WebApplicationException(e.getMessage(), 400);
    }
  }

  @Override
  public WarehouseSearchResult searchWarehouseUnits(
      String location,
      BigInteger minCapacity,
      BigInteger maxCapacity,
      String sortBy,
      String sortOrder,
      BigInteger page,
      BigInteger pageSize) {

    int resolvedPage = page != null ? page.intValue() : 0;
    if (resolvedPage < 0) {
      throw new WebApplicationException("page must not be negative", 400);
    }

    int resolvedPageSize = pageSize != null ? pageSize.intValue() : 10;
    if (resolvedPageSize < 1 || resolvedPageSize > 100) {
      throw new WebApplicationException("pageSize must be between 1 and 100", 400);
    }

    WarehouseSearchPage result =
        warehouseRepository.search(
            location,
            minCapacity != null ? minCapacity.intValue() : null,
            maxCapacity != null ? maxCapacity.intValue() : null,
            sortBy,
            sortOrder,
            resolvedPage,
            resolvedPageSize);

    var response = new WarehouseSearchResult();
    response.setItems(result.items().stream().map(this::toWarehouseResponse).toList());
    response.setPage(result.page());
    response.setPageSize(result.pageSize());
    response.setTotalElements((int) result.totalElements());
    return response;
  }

  private Warehouse toWarehouseResponse(
      com.fulfilment.application.monolith.warehouses.domain.models.Warehouse warehouse) {
    var response = new Warehouse();
    response.setBusinessUnitCode(warehouse.businessUnitCode);
    response.setLocation(warehouse.location);
    response.setCapacity(warehouse.capacity);
    response.setStock(warehouse.stock);

    return response;
  }
}

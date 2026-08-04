package com.fulfilment.application.monolith.warehouses.domain.models;

import java.util.List;

public record WarehouseSearchPage(List<Warehouse> items, int page, int pageSize, long totalElements) {}

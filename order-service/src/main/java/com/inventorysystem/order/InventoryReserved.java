package com.inventorysystem.order;

/**
 * Order Service's own copy of the event Inventory Service publishes when it
 * successfully reserved stock. Same shape, different package - the services
 * share a message contract, not a class.
 */
public record InventoryReserved(Long orderId, String item, int quantity) {
}

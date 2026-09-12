package com.inventorysystem.order;

/**
 * Order Service's own copy of the event Inventory Service publishes when stock
 * could not be reserved.
 */
public record InventoryFailed(Long orderId, String item, int quantity) {
}

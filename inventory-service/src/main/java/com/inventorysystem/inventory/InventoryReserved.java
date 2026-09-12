package com.inventorysystem.inventory;

/**
 * Published to "inventory-events" when stock was successfully decremented.
 */
public record InventoryReserved(Long orderId, String item, int quantity) {
}

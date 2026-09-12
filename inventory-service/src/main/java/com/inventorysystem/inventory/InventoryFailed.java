package com.inventorysystem.inventory;

/**
 * Published to "inventory-events" when stock could not be reserved, either
 * because the item is not stocked or because there is not enough of it.
 */
public record InventoryFailed(Long orderId, String item, int quantity) {
}

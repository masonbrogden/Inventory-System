package com.inventorysystem.inventory;

/**
 * Inventory Service's own copy of the event published by Order Service.
 *
 * Deliberately a separate class from order-service's OrderCreated: the two
 * services share a message shape, not code. Either can change its internal
 * class without the other needing to be recompiled.
 */
public record OrderCreated(Long orderId, String item, int quantity) {
}

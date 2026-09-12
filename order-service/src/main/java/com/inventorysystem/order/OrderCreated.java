package com.inventorysystem.order;

/**
 * Event published to the "order-events" topic whenever a new order is saved.
 *
 * A record is an immutable data carrier: the compiler writes the constructor,
 * the accessors (orderId(), item(), quantity()), equals, hashCode and toString.
 * Events should never change after being published, which is exactly what a
 * record gives us.
 */
public record OrderCreated(Long orderId, String item, int quantity) {
}

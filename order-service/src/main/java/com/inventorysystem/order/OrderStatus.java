package com.inventorysystem.order;

/**
 * The lifecycle states an order can be in.
 *
 * A new order starts as PENDING. Later, once Inventory Service is involved,
 * it will move to CONFIRMED or CANCELLED.
 */
public enum OrderStatus {
	PENDING,
	CONFIRMED,
	CANCELLED
}

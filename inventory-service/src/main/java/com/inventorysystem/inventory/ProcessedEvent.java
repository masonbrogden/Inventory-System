package com.inventorysystem.inventory;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A record that one OrderCreated event has already been handled.
 *
 * Order Service publishes exactly one OrderCreated per order, so the order id
 * doubles as the event id. Note there is no @GeneratedValue: we supply the key
 * ourselves, which means the table's PRIMARY KEY constraint is what physically
 * prevents the same event being recorded twice.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

	@Id
	private Long orderId;

	// Not used by the logic - kept so you can see when something was handled
	// when inspecting the table.
	private Instant processedAt;

	// Required by JPA.
	protected ProcessedEvent() {
	}

	public ProcessedEvent(Long orderId) {
		this.orderId = orderId;
		this.processedAt = Instant.now();
	}

	public Long getOrderId() {
		return orderId;
	}

	public Instant getProcessedAt() {
		return processedAt;
	}
}

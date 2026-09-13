package com.inventorysystem.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
// "order" is a reserved word in SQL (as in ORDER BY), so the table cannot use
// the default name derived from the class. "orders" is the conventional fix.
@Table(name = "orders")
@Schema(description = "A request for some quantity of a single stock item.")
public class Order {

	@Id
	// Let PostgreSQL generate the id with its own identity column.
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Schema(description = "Order id, assigned by the database. Ignored if sent.",
			accessMode = Schema.AccessMode.READ_ONLY)
	private Long id;

	@Schema(description = "Name of the stock item being ordered.")
	private String item;

	@Schema(description = "Number of units requested.")
	private int quantity;

	// Store the enum as its name ("PENDING") rather than its position (0).
	@Enumerated(EnumType.STRING)
	@Schema(description = "Set by the server: PENDING on creation, then CONFIRMED if stock was reserved or CANCELLED if not. Ignored if sent.",
			accessMode = Schema.AccessMode.READ_ONLY)
	private OrderStatus status;

	// JPA requires a no-argument constructor to build instances when reading rows.
	public Order() {
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getItem() {
		return item;
	}

	public void setItem(String item) {
		this.item = item;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}

	public OrderStatus getStatus() {
		return status;
	}

	public void setStatus(OrderStatus status) {
		this.status = status;
	}
}

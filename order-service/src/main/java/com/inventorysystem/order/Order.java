package com.inventorysystem.order;

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
public class Order {

	@Id
	// Let PostgreSQL generate the id with its own identity column.
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String item;

	private int quantity;

	// Store the enum as its name ("PENDING") rather than its position (0).
	@Enumerated(EnumType.STRING)
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

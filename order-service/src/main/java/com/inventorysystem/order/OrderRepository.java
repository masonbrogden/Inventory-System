package com.inventorysystem.order;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for Order rows.
 *
 * There is no implementation to write: Spring Data JPA generates one at startup
 * from this interface. JpaRepository<Order, Long> means "manages Order entities
 * whose id is a Long" and supplies save, findById, findAll, deleteById, etc.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {
}

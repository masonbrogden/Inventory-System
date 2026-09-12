package com.inventorysystem.inventory;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StockItemRepository extends JpaRepository<StockItem, Long> {

	/**
	 * Spring Data builds the query from the method name alone: "findBy" plus the
	 * "item" field becomes SELECT * FROM stock_items WHERE item = ?
	 *
	 * Optional because the item may not be stocked at all.
	 */
	Optional<StockItem> findByItem(String item);
}

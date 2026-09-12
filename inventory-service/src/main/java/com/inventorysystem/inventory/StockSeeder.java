package com.inventorysystem.inventory;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Puts three sample items in the database so there is something to reserve
 * against. CommandLineRunner runs once, after the application context is fully
 * built and before the app starts handling work.
 */
@Component
public class StockSeeder implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(StockSeeder.class);

	private final StockItemRepository stockItemRepository;

	public StockSeeder(StockItemRepository stockItemRepository) {
		this.stockItemRepository = stockItemRepository;
	}

	@Override
	public void run(String... args) {
		// The table survives restarts, so only seed when it is empty. Without
		// this guard every restart would try to insert duplicate item names and
		// fail the unique constraint.
		if (stockItemRepository.count() > 0) {
			log.info("Stock already seeded, skipping");
			return;
		}

		stockItemRepository.saveAll(List.of(
				new StockItem("widget", 10),
				new StockItem("gadget", 5),
				new StockItem("gizmo", 2)));

		log.info("Seeded 3 stock items");
	}
}

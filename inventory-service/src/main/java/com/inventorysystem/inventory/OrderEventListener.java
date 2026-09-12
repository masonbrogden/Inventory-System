package com.inventorysystem.inventory;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

	private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

	private static final String OUTPUT_TOPIC = "inventory-events";

	private final StockItemRepository stockItemRepository;
	// Object, because this template sends two different event types.
	private final KafkaTemplate<String, Object> kafkaTemplate;

	public OrderEventListener(StockItemRepository stockItemRepository,
			KafkaTemplate<String, Object> kafkaTemplate) {
		this.stockItemRepository = stockItemRepository;
		this.kafkaTemplate = kafkaTemplate;
	}

	@KafkaListener(topics = "order-events")
	public void onOrderCreated(OrderCreated event) {
		log.info("Received {}", event);

		Optional<StockItem> found = stockItemRepository.findByItem(event.item());

		if (found.isEmpty()) {
			log.info("Item '{}' is not stocked - failing order {}", event.item(), event.orderId());
			publishFailed(event);
			return;
		}

		StockItem stock = found.get();
		if (stock.getQuantityAvailable() < event.quantity()) {
			log.info("Not enough '{}': have {}, need {} - failing order {}",
					event.item(), stock.getQuantityAvailable(), event.quantity(), event.orderId());
			publishFailed(event);
			return;
		}

		stock.setQuantityAvailable(stock.getQuantityAvailable() - event.quantity());
		stockItemRepository.save(stock);
		log.info("Reserved {} x '{}' for order {} - {} left",
				event.quantity(), event.item(), event.orderId(), stock.getQuantityAvailable());

		kafkaTemplate.send(OUTPUT_TOPIC,
				new InventoryReserved(event.orderId(), event.item(), event.quantity()));
	}

	private void publishFailed(OrderCreated event) {
		kafkaTemplate.send(OUTPUT_TOPIC,
				new InventoryFailed(event.orderId(), event.item(), event.quantity()));
	}
}

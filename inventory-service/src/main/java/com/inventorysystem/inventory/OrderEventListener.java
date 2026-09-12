package com.inventorysystem.inventory;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderEventListener {

	private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

	private static final String OUTPUT_TOPIC = "inventory-events";

	private final StockItemRepository stockItemRepository;
	private final ProcessedEventRepository processedEventRepository;
	// Object, because this template sends two different event types.
	private final KafkaTemplate<String, Object> kafkaTemplate;

	public OrderEventListener(StockItemRepository stockItemRepository,
			ProcessedEventRepository processedEventRepository,
			KafkaTemplate<String, Object> kafkaTemplate) {
		this.stockItemRepository = stockItemRepository;
		this.processedEventRepository = processedEventRepository;
		this.kafkaTemplate = kafkaTemplate;
	}

	/**
	 * @Transactional is load-bearing here, not decoration. The duplicate check,
	 * the stock decrement and the "already processed" record all commit together
	 * or not at all. Without it, a crash after decrementing but before recording
	 * would leave the event eligible for redelivery and stock would drop twice.
	 */
	@KafkaListener(topics = "order-events")
	@Transactional
	public void onOrderCreated(OrderCreated event) {
		log.info("Received {}", event);

		if (processedEventRepository.existsById(event.orderId())) {
			log.info("Order {} already processed - ignoring duplicate delivery", event.orderId());
			return;
		}

		Optional<StockItem> found = stockItemRepository.findByItem(event.item());

		if (found.isEmpty()) {
			log.info("Item '{}' is not stocked - failing order {}", event.item(), event.orderId());
			publishFailed(event);
		} else {
			StockItem stock = found.get();
			if (stock.getQuantityAvailable() < event.quantity()) {
				log.info("Not enough '{}': have {}, need {} - failing order {}",
						event.item(), stock.getQuantityAvailable(), event.quantity(), event.orderId());
				publishFailed(event);
			} else {
				stock.setQuantityAvailable(stock.getQuantityAvailable() - event.quantity());
				stockItemRepository.save(stock);
				log.info("Reserved {} x '{}' for order {} - {} left",
						event.quantity(), event.item(), event.orderId(), stock.getQuantityAvailable());
				kafkaTemplate.send(OUTPUT_TOPIC,
						new InventoryReserved(event.orderId(), event.item(), event.quantity()));
			}
		}

		// Marked on every path, success or failure. A duplicate of a failed
		// event must not republish InventoryFailed either.
		processedEventRepository.save(new ProcessedEvent(event.orderId()));
	}

	private void publishFailed(OrderCreated event) {
		kafkaTemplate.send(OUTPUT_TOPIC,
				new InventoryFailed(event.orderId(), event.item(), event.quantity()));
	}
}

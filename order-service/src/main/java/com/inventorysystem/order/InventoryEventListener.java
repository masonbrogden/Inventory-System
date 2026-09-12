package com.inventorysystem.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to Inventory Service's verdict and closes the loop on the order.
 *
 * Both event types arrive on the same topic, so @KafkaListener is on the class
 * and each @KafkaHandler method claims one payload type. Spring picks the
 * method whose parameter matches the deserialized message.
 */
@Component
@KafkaListener(topics = "inventory-events")
public class InventoryEventListener {

	private static final Logger log = LoggerFactory.getLogger(InventoryEventListener.class);

	private final OrderRepository orderRepository;

	public InventoryEventListener(OrderRepository orderRepository) {
		this.orderRepository = orderRepository;
	}

	@KafkaHandler
	public void onInventoryReserved(InventoryReserved event) {
		log.info("Received {}", event);
		updateStatus(event.orderId(), OrderStatus.CONFIRMED);
	}

	@KafkaHandler
	public void onInventoryFailed(InventoryFailed event) {
		log.info("Received {}", event);
		updateStatus(event.orderId(), OrderStatus.CANCELLED);
	}

	private void updateStatus(Long orderId, OrderStatus status) {
		orderRepository.findById(orderId).ifPresentOrElse(
				order -> {
					order.setStatus(status);
					orderRepository.save(order);
					log.info("Order {} -> {}", orderId, status);
				},
				() -> log.warn("No order {} found, ignoring event", orderId));
	}
}

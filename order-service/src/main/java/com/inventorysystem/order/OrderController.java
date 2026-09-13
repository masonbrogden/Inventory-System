package com.inventorysystem.order;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

	private static final String TOPIC = "order-events";

	private final OrderRepository orderRepository;
	private final KafkaTemplate<String, OrderCreated> kafkaTemplate;

	// Spring injects both collaborators through this single constructor.
	public OrderController(OrderRepository orderRepository,
			KafkaTemplate<String, OrderCreated> kafkaTemplate) {
		this.orderRepository = orderRepository;
		this.kafkaTemplate = kafkaTemplate;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Place an order: saves it as PENDING and publishes an OrderCreated event so Inventory Service can reserve the stock.")
	public Order create(@RequestBody Order order) {
		// Ignore any id sent by the client so this always inserts a new row
		// rather than updating an existing one.
		order.setId(null);
		// Status is decided by the server, not the caller.
		order.setStatus(OrderStatus.PENDING);
		Order saved = orderRepository.save(order);

		// Published after the save, so the event always carries a real database id.
		kafkaTemplate.send(TOPIC,
				new OrderCreated(saved.getId(), saved.getItem(), saved.getQuantity()));

		return saved;
	}
}

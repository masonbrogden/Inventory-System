package com.inventorysystem.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Plain unit tests for the stock-decrement logic. No Spring context, no
 * database, no Kafka broker - the collaborators are mocks, so these run in
 * milliseconds and need nothing running.
 */
@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

	@Mock
	private StockItemRepository stockItemRepository;

	@Mock
	private ProcessedEventRepository processedEventRepository;

	@Mock
	private KafkaTemplate<String, Object> kafkaTemplate;

	private OrderEventListener listener;

	@BeforeEach
	void setUp() {
		listener = new OrderEventListener(stockItemRepository, processedEventRepository, kafkaTemplate);
	}

	@Test
	void decrementsStockAndPublishesReservedWhenEnoughIsAvailable() {
		StockItem widget = new StockItem("widget", 10);
		when(processedEventRepository.existsById(1L)).thenReturn(false);
		when(stockItemRepository.findByItem("widget")).thenReturn(Optional.of(widget));

		listener.onOrderCreated(new OrderCreated(1L, "widget", 4));

		assertThat(widget.getQuantityAvailable()).isEqualTo(6);
		verify(stockItemRepository).save(widget);
		verify(kafkaTemplate).send("inventory-events", new InventoryReserved(1L, "widget", 4));
	}

	@Test
	void leavesStockAloneAndPublishesFailedWhenThereIsNotEnough() {
		StockItem gizmo = new StockItem("gizmo", 2);
		when(processedEventRepository.existsById(2L)).thenReturn(false);
		when(stockItemRepository.findByItem("gizmo")).thenReturn(Optional.of(gizmo));

		listener.onOrderCreated(new OrderCreated(2L, "gizmo", 99));

		assertThat(gizmo.getQuantityAvailable()).isEqualTo(2);
		verify(stockItemRepository, never()).save(any());
		verify(kafkaTemplate).send("inventory-events", new InventoryFailed(2L, "gizmo", 99));
	}

	@Test
	void ignoresAnEventItHasAlreadyProcessed() {
		when(processedEventRepository.existsById(3L)).thenReturn(true);

		listener.onOrderCreated(new OrderCreated(3L, "widget", 1));

		// Never even looks at stock, and publishes nothing.
		verify(stockItemRepository, never()).findByItem(anyString());
		verify(stockItemRepository, never()).save(any());
		verify(kafkaTemplate, never()).send(anyString(), any());
	}
}

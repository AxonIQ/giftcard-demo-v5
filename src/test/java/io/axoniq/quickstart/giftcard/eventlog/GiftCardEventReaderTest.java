package io.axoniq.quickstart.giftcard.eventlog;

import io.axoniq.quickstart.giftcard.event.GiftCardIssuedEvent;
import io.axoniq.quickstart.giftcard.event.GiftCardRedeemedEvent;
import org.axonframework.conversion.PassThroughConverter;
import org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.StorageEngineBackedEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.core.EmptyApplicationContext;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.SimpleEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link GiftCardEventReader} sources the correct events for a gift card directly from the
 * event store (filtered by the {@code giftCardId} tag) and folds them into arbitrary results.
 *
 * <p>The test wires a real in-memory {@link EventStore} (no Axon Server, no JPA) so the tag-based
 * {@link org.axonframework.messaging.eventstreaming.EventCriteria criteria} sourcing exercised by the reader
 * runs against genuine framework infrastructure rather than a mock.</p>
 */
class GiftCardEventReaderTest {

    /**
     * Folds a gift card's events into its remaining balance, adding issued amounts and subtracting redeemed
     * amounts. Demonstrates the reactive fold the reader exposes.
     */
    private static final BiFunction<BigDecimal, EventMessage, BigDecimal> BALANCE = (total, event) ->
            switch (event.type().qualifiedName().localName()) {
                case "GiftCardIssuedEvent" ->
                        total.add(event.payloadAs(GiftCardIssuedEvent.class, PassThroughConverter.INSTANCE).amount());
                case "GiftCardRedeemedEvent" ->
                        total.subtract(event.payloadAs(GiftCardRedeemedEvent.class, PassThroughConverter.INSTANCE).amount());
                default -> total;
            };

    private EventStore eventStore;
    private UnitOfWorkFactory unitOfWorkFactory;
    private GiftCardEventReader reader;

    @BeforeEach
    void setUp() {
        eventStore = new StorageEngineBackedEventStore(
                new InMemoryEventStorageEngine(),
                new SimpleEventBus(),
                new AnnotationBasedTagResolver()
        );
        unitOfWorkFactory = new SimpleUnitOfWorkFactory(EmptyApplicationContext.INSTANCE);
        reader = new GiftCardEventReader(eventStore, unitOfWorkFactory);
    }

    /**
     * Publishes the given events to the in-memory event store, tagging them via the configured
     * {@link AnnotationBasedTagResolver} so they can later be sourced by their {@code giftCardId} tag.
     */
    private void publish(EventMessage... events) {
        unitOfWorkFactory.create()
                         .executeWithResult(context -> eventStore.publish(context, events))
                         .join();
    }

    private EventMessage event(Object payload) {
        return new GenericEventMessage(new MessageType(payload.getClass()), payload);
    }

    @Nested
    class Source {

        @Test
        void foldsEventsIntoTheRemainingBalance() {
            // given
            String giftCardId = "card-1";
            publish(
                    event(new GiftCardIssuedEvent(giftCardId, new BigDecimal("100.00"))),
                    event(new GiftCardRedeemedEvent(giftCardId, new BigDecimal("30.00")))
            );

            // when
            BigDecimal balance = reader.source(giftCardId, BigDecimal.ZERO, BALANCE).join();

            // then
            assertThat(balance).isEqualByComparingTo("70.00");
        }

        @Test
        void foldsEventsForRequestedGiftCardOnly() {
            // given
            publish(
                    event(new GiftCardIssuedEvent("card-1", new BigDecimal("100.00"))),
                    event(new GiftCardIssuedEvent("card-2", new BigDecimal("50.00"))),
                    event(new GiftCardRedeemedEvent("card-1", new BigDecimal("10.00")))
            );

            // when
            BigDecimal balanceCard1 = reader.source("card-1", BigDecimal.ZERO, BALANCE).join();
            BigDecimal balanceCard2 = reader.source("card-2", BigDecimal.ZERO, BALANCE).join();

            // then
            assertThat(balanceCard1).isEqualByComparingTo("90.00");
            assertThat(balanceCard2).isEqualByComparingTo("50.00");
        }

        @Test
        void foldsEventsIntoAListInOccurrenceOrder() {
            // given
            String giftCardId = "card-1";
            publish(
                    event(new GiftCardIssuedEvent(giftCardId, new BigDecimal("100.00"))),
                    event(new GiftCardRedeemedEvent(giftCardId, new BigDecimal("30.00")))
            );

            // when
            List<String> types = reader.source(giftCardId, new ArrayList<String>(), (list, event) -> {
                list.add(event.type().qualifiedName().localName());
                return list;
            }).join();

            // then
            assertThat(types).containsExactly("GiftCardIssuedEvent", "GiftCardRedeemedEvent");
        }

        @Test
        void returnsIdentityForUnknownGiftCard() {
            // given
            publish(event(new GiftCardIssuedEvent("card-1", new BigDecimal("100.00"))));

            // when
            BigDecimal balance = reader.source("does-not-exist", BigDecimal.ZERO, BALANCE).join();

            // then
            assertThat(balance).isEqualByComparingTo("0.00");
        }
    }
}

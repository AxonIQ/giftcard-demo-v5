package io.axoniq.quickstart.giftcard.eventlog;

import io.axoniq.quickstart.giftcard.domain.GiftCard;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * Sources the events of a single gift card directly from the {@link EventStore} and folds them into a result.
 *
 * <p>This component demonstrates Dynamic Consistency Boundary (DCB) style event sourcing: rather than querying
 * the aggregated read model maintained by the {@link io.axoniq.quickstart.giftcard.query.GiftCardProjection},
 * it selects the events for one gift card straight from the event store. Because {@link GiftCard} declares
 * {@code @EventSourced(tagKey = "giftCardId")} and its events carry an
 * {@link org.axonframework.eventsourcing.annotation.EventTag @EventTag} on their {@code giftCardId}, every
 * event is stored with a {@code giftCardId} tag, which is used here to select exactly one gift card's events
 * via {@link EventCriteria#havingTags(String...)}.</p>
 *
 * <p><strong>Folding instead of a fixed shape:</strong> reading events is a fold over a finite
 * {@link org.axonframework.messaging.core.MessageStream stream}. Callers supply an {@code identity} and an
 * {@code accumulator}, so a single method serves every use case: sum a balance, count events, collect them
 * into a list, and so on. For example, to compute the remaining balance:</p>
 * <pre>{@code
 * CompletableFuture<BigDecimal> balance = reader.source(giftCardId, BigDecimal.ZERO, (total, event) ->
 *         switch (event.type().qualifiedName().localName()) {
 *             case "GiftCardIssuedEvent"   -> total.add(event.payloadAs(GiftCardIssuedEvent.class, converter).amount());
 *             case "GiftCardRedeemedEvent" -> total.subtract(event.payloadAs(GiftCardRedeemedEvent.class, converter).amount());
 *             default -> total;
 *         });
 * }</pre>
 *
 * <p><strong>No database transaction is involved.</strong> The read only needs a {@code ProcessingContext},
 * which the {@link UnitOfWorkFactory#create() unit of work} supplies; there is nothing to commit for a
 * read-only fold. The whole operation stays non-blocking by returning the {@link CompletableFuture} produced
 * by the unit of work directly to the caller.</p>
 *
 * <p><strong>Framework integration:</strong> Spring injects the {@link EventStore} and {@link UnitOfWorkFactory}
 * beans that Axon Framework auto-configures. This component is in turn injected into the
 * {@link io.axoniq.quickstart.giftcard.web.GiftCardController} to serve the per-gift-card event endpoint.</p>
 *
 * @see EventCriteria
 * @see SourcingCondition
 * @see EventStore
 * @see org.axonframework.messaging.core.MessageStream#reduce(Object, BiFunction)
 *
 * @author AxonIQ Quickstart
 * @version 1.0
 * @since 1.0
 */
@Component
public class GiftCardEventReader {

    /**
     * Tag key under which every gift card event is stored, matching
     * {@code @EventSourced(tagKey = "giftCardId")} on {@link GiftCard}.
     */
    private static final String GIFT_CARD_ID_TAG = "giftCardId";

    private final EventStore eventStore;
    private final UnitOfWorkFactory unitOfWorkFactory;

    /**
     * Constructs a new {@code GiftCardEventReader}.
     *
     * @param eventStore        the event store to source gift card events from
     * @param unitOfWorkFactory the factory used to obtain a {@code ProcessingContext} for the read
     */
    public GiftCardEventReader(EventStore eventStore, UnitOfWorkFactory unitOfWorkFactory) {
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore may not be null");
        this.unitOfWorkFactory = Objects.requireNonNull(unitOfWorkFactory, "unitOfWorkFactory may not be null");
    }

    /**
     * Sources every event stored for the given {@code giftCardId} and folds them into a single result, in the
     * order they occurred.
     *
     * <p>An unknown or never-issued gift card yields the {@code identity} unchanged, since no events carry its
     * tag.</p>
     *
     * @param giftCardId  the unique identifier of the gift card whose events should be read
     * @param identity    the initial accumulator value
     * @param accumulator combines the running result with each sourced {@link EventMessage}
     * @param <R>         the type of the folded result
     * @return a future that completes with the folded result over the gift card's events
     */
    public <R> CompletableFuture<R> source(String giftCardId,
                                           R identity,
                                           BiFunction<R, EventMessage, R> accumulator) {
        SourcingCondition condition = SourcingCondition.conditionFor(
                EventCriteria.havingTags(GIFT_CARD_ID_TAG, giftCardId)
        );
        return unitOfWorkFactory.create().executeWithResult(processingContext ->
                eventStore.transaction(processingContext)
                          .source(condition)
                          .reduce(identity, (result, entry) -> accumulator.apply(result, entry.message()))
        );
    }
}

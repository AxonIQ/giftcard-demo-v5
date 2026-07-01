package io.axoniq.quickstart.giftcard.query;

import io.axoniq.quickstart.giftcard.domain.GiftCard;
import io.axoniq.quickstart.giftcard.event.GiftCardIssuedEvent;
import io.axoniq.quickstart.giftcard.event.GiftCardRedeemedEvent;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.queryhandling.QueryUpdateEmitter;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Event-driven projection that maintains a disk-backed read model of gift card data in an H2 database via JDBC.
 *
 * <p>This component implements the CQRS query side by maintaining a denormalized, eventually consistent view
 * of gift card states. It listens to domain events from the {@link GiftCard} EventSourced DCB model and
 * persists the read model to the {@code gift_card_summary} table using a Spring {@link JdbcTemplate}, while
 * also supporting real-time query subscriptions through Axon Framework's query update mechanism.</p>
 *
 * <p><strong>Why a database instead of memory:</strong> because the read model is stored in H2 with on-disk
 * storage, it survives application restarts. Combined with the persistent {@code JdbcTokenStore} that Axon
 * auto-configures from the {@code DataSource}, the projection resumes from its last processed event on restart
 * rather than rebuilding from the entire event stream.</p>
 *
 * <p><strong>Why JDBC rather than JPA:</strong> using plain JDBC avoids introducing a Hibernate
 * {@code EntityManagerFactory}, whose asynchronous bootstrap executor would otherwise form an initialization
 * cycle with Axon's transaction manager under {@code @EnableScheduling}. The schema (both this projection's
 * table and Axon's {@code TokenEntry} table) is created from {@code src/main/resources/schema.sql}.</p>
 *
 * <p><strong>Transactions:</strong> Axon's Spring integration bridges the Spring
 * {@code DataSourceTransactionManager} to event processing, so each event handler's JDBC write commits in the
 * same transaction as the tracking-token update. Because token and read model commit atomically, each event
 * is applied exactly once - which is what makes the relative {@code remaining_value} update for redemptions
 * safe.</p>
 *
 * @see GiftCardSummary
 * @see FindGiftCardQuery
 * @see FindAllGiftCardsQuery
 * @see <a href="https://docs.axoniq.io/reference-guide/">Axon Framework Reference Guide</a>
 *
 * @author AxonIQ Quickstart
 * @version 1.0
 * @since 1.0
 */
@Component
public class GiftCardProjection {

    private static final String UPSERT =
            "MERGE INTO gift_card_summary (gift_card_id, remaining_value, initial_value) "
                    + "KEY (gift_card_id) VALUES (?, ?, ?)";
    private static final String REDEEM =
            "UPDATE gift_card_summary SET remaining_value = remaining_value - ? WHERE gift_card_id = ?";
    private static final String SELECT_BY_ID =
            "SELECT gift_card_id, remaining_value, initial_value FROM gift_card_summary WHERE gift_card_id = ?";
    private static final String SELECT_ALL =
            "SELECT gift_card_id, remaining_value, initial_value FROM gift_card_summary";

    private static final RowMapper<GiftCardSummary> ROW_MAPPER = (rs, rowNum) -> new GiftCardSummary(
            rs.getString("gift_card_id"),
            rs.getBigDecimal("remaining_value"),
            rs.getBigDecimal("initial_value")
    );

    private final JdbcTemplate jdbcTemplate;

    /**
     * Constructs a new {@code GiftCardProjection}.
     *
     * @param jdbcTemplate the template used to persist and query gift card summaries in H2
     */
    public GiftCardProjection(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Event handler for gift card issued events.
     *
     * <p>Invoked by the Axon Framework when a {@link GiftCardIssuedEvent} is published. It upserts a
     * {@code gift_card_summary} row (idempotent, so replays are safe) and notifies any active subscription
     * queries.</p>
     *
     * @param event              the gift card issued event containing ID and initial amount
     * @param queryUpdateEmitter the emitter used to push updates to subscription queries
     */
    @EventHandler
    public void on(GiftCardIssuedEvent event, QueryUpdateEmitter queryUpdateEmitter) {
        jdbcTemplate.update(UPSERT, event.giftCardId(), event.amount(), event.amount());
        emitUpdates(new GiftCardSummary(event.giftCardId(), event.amount(), event.amount()), queryUpdateEmitter);
    }

    /**
     * Event handler for gift card redeemed events.
     *
     * <p>Invoked by the Axon Framework when a {@link GiftCardRedeemedEvent} is published. It reduces the
     * persisted remaining balance by the redeemed amount and notifies subscription query subscribers. A missing
     * row is ignored defensively (the {@code UPDATE} simply affects no rows), though this should not occur in
     * normal operation.</p>
     *
     * @param event              the gift card redeemed event containing ID and redeemed amount
     * @param queryUpdateEmitter the emitter used to push updates to subscription queries
     */
    @EventHandler
    public void on(GiftCardRedeemedEvent event, QueryUpdateEmitter queryUpdateEmitter) {
        jdbcTemplate.update(REDEEM, event.amount(), event.giftCardId());
        findById(event.giftCardId()).ifPresent(summary -> emitUpdates(summary, queryUpdateEmitter));
    }

    /**
     * Query handler for retrieving a specific gift card by ID from the H2 database.
     *
     * <p>Supports both synchronous queries and subscription queries. For subscription queries, clients receive
     * the initial result immediately, followed by real-time updates whenever the gift card state changes.</p>
     *
     * @param query the query containing the gift card ID to look up
     * @return the gift card summary if found, {@code null} if no gift card exists with the given ID
     */
    @QueryHandler
    public GiftCardSummary handle(FindGiftCardQuery query) {
        return findById(query.giftCardId()).orElse(null);
    }

    /**
     * Query handler for retrieving all gift cards from the H2 database.
     *
     * @param query the parameterless query for all gift cards
     * @return a {@link GiftCardSummaryList} containing all gift card summaries
     */
    @QueryHandler
    public GiftCardSummaryList handle(FindAllGiftCardsQuery query) {
        return new GiftCardSummaryList(jdbcTemplate.query(SELECT_ALL, ROW_MAPPER));
    }

    private Optional<GiftCardSummary> findById(String giftCardId) {
        return jdbcTemplate.query(SELECT_BY_ID, ROW_MAPPER, giftCardId).stream().findFirst();
    }

    private void emitUpdates(GiftCardSummary summary, QueryUpdateEmitter queryUpdateEmitter) {
        queryUpdateEmitter.emit(FindGiftCardQuery.class,
                query -> query.giftCardId().equals(summary.giftCardId()),
                summary);
        queryUpdateEmitter.emit(FindAllGiftCardsQuery.class,
                query -> true,
                summary);
    }
}

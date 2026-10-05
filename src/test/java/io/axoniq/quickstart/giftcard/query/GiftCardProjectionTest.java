package io.axoniq.quickstart.giftcard.query;

import io.axoniq.quickstart.giftcard.event.GiftCardIssuedEvent;
import org.axonframework.messaging.core.configuration.MessagingConfigurer;
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the {@link GiftCardProjection} event and query handlers, driven by {@link AxonTestFixture}. The
 * projection is registered on an in-memory subscribing event processor (so events are applied synchronously)
 * and as a query handling component, backed by a fresh in-memory H2 database per test. The fixture publishes
 * events; assertions read the resulting read model back through the {@link QueryGateway}.
 */
class GiftCardProjectionTest {

    private AxonTestFixture fixture;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:giftcard-proj-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE gift_card_summary (
                    gift_card_id    VARCHAR(255) NOT NULL,
                    remaining_value DECIMAL(19, 2),
                    initial_value   DECIMAL(19, 2),
                    PRIMARY KEY (gift_card_id)
                )""");

        var projection = new GiftCardProjection(jdbcTemplate);
        var configurer = MessagingConfigurer.create()
                .eventProcessing(ep -> ep
                        .subscribing(sub -> sub
                                .processor("giftcard-projection",
                                        phase -> phase.eventHandlingComponents(
                                                components -> components.autodetected("giftcard-projection",
                                                        config -> projection))
                                                .notCustomized())))
                .registerQueryHandlingModule(
                        QueryHandlingModule.named("giftcard-queries")
                                .queryHandlers()
                                .autodetectedQueryHandlingComponent(c -> projection));
        fixture = AxonTestFixture.with(configurer);
    }

    @AfterEach
    void tearDown() {
        fixture.stop();
    }

    @Test
    void issuedEventCreatesAQueryableSummary() {
        // given / when
        fixture.given().noPriorActivity()
               .when().event(new GiftCardIssuedEvent("card-1", new BigDecimal("100.00")))
               // then
               .then().expect(config -> {
                   GiftCardSummary summary = config.getComponent(QueryGateway.class)
                           .query(new FindGiftCardQuery("card-1"), GiftCardSummary.class)
                           .join();
                   assertThat(summary).isNotNull();
                   assertThat(summary.giftCardId()).isEqualTo("card-1");
                   assertThat(summary.initialValue()).isEqualByComparingTo("100.00");
                   assertThat(summary.remainingValue()).isEqualByComparingTo("100.00");
               });
    }
}

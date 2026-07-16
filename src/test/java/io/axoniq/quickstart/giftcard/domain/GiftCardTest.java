package io.axoniq.quickstart.giftcard.domain;

import io.axoniq.quickstart.giftcard.command.IssueGiftCardCommand;
import io.axoniq.quickstart.giftcard.command.RedeemGiftCardCommand;
import io.axoniq.quickstart.giftcard.event.GiftCardIssuedEvent;
import io.axoniq.quickstart.giftcard.event.GiftCardRedeemedEvent;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.messaging.core.configuration.MessagingConfigurer;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

/**
 * Given/when/then tests for the {@link GiftCard} event-sourced entity, driven by {@link AxonTestFixture}. The
 * fixture runs a fully in-memory Axon configuration - no Axon Server or database - so it exercises the real
 * command handling, event sourcing, and business validation of the entity.
 */
class GiftCardTest {

    private static final String CARD_ID = "card-1";

    private AxonTestFixture fixture;

    @BeforeEach
    void setUp() {
        var configurer = MessagingConfigurer.create();
        configurer.componentRegistry(cr -> cr.registerModule(
                EventSourcedEntityModule.autodetected(String.class, GiftCard.class)
        ));
        fixture = AxonTestFixture.with(configurer);
    }

    @AfterEach
    void tearDown() {
        fixture.stop();
    }

    @Nested
    class Issuing {

        @Test
        void publishesGiftCardIssuedEvent() {
            // given / when / then
            fixture.given()
                   .noPriorActivity()
                   .when()
                   .command(new IssueGiftCardCommand(CARD_ID, new BigDecimal("100.00")))
                   .then()
                   .events(new GiftCardIssuedEvent(CARD_ID, new BigDecimal("100.00")));
        }

        @Test
        void rejectsANonPositiveAmount() {
            // given / when / then
            fixture.given()
                   .noPriorActivity()
                   .when()
                   .command(new IssueGiftCardCommand(CARD_ID, BigDecimal.ZERO))
                   .then()
                   .exception(IllegalArgumentException.class, "Gift card amount must be positive")
                   .noEvents();
        }

        @Test
        void rejectsIssuingAnAlreadyIssuedCard() {
            // given
            fixture.given()
                   .event(new GiftCardIssuedEvent(CARD_ID, new BigDecimal("100.00")))
                   // when
                   .when()
                   .command(new IssueGiftCardCommand(CARD_ID, new BigDecimal("50.00")))
                   // then
                   .then()
                   .exception(IllegalStateException.class, "Gift card already issued")
                   .noEvents();
        }
    }

    @Nested
    class Redeeming {

        @Test
        void publishesGiftCardRedeemedEvent() {
            // given
            fixture.given()
                   .event(new GiftCardIssuedEvent(CARD_ID, new BigDecimal("100.00")))
                   // when
                   .when()
                   .command(new RedeemGiftCardCommand(CARD_ID, new BigDecimal("30.00")))
                   // then
                   .then()
                   .events(new GiftCardRedeemedEvent(CARD_ID, new BigDecimal("30.00")));
        }

        @Test
        void allowsRedeemingTheEntireRemainingBalance() {
            // given
            fixture.given()
                   .event(new GiftCardIssuedEvent(CARD_ID, new BigDecimal("100.00")))
                   .event(new GiftCardRedeemedEvent(CARD_ID, new BigDecimal("40.00")))
                   // when
                   .when()
                   .command(new RedeemGiftCardCommand(CARD_ID, new BigDecimal("60.00")))
                   // then
                   .then()
                   .events(new GiftCardRedeemedEvent(CARD_ID, new BigDecimal("60.00")));
        }

        @Test
        void rejectsRedeemingMoreThanTheRemainingBalance() {
            // given
            fixture.given()
                   .event(new GiftCardIssuedEvent(CARD_ID, new BigDecimal("100.00")))
                   .event(new GiftCardRedeemedEvent(CARD_ID, new BigDecimal("80.00")))
                   // when
                   .when()
                   .command(new RedeemGiftCardCommand(CARD_ID, new BigDecimal("30.00")))
                   // then
                   .then()
                   .exception(IllegalArgumentException.class, "Insufficient funds")
                   .noEvents();
        }

        @Test
        void rejectsANonPositiveAmount() {
            // given
            fixture.given()
                   .event(new GiftCardIssuedEvent(CARD_ID, new BigDecimal("100.00")))
                   // when
                   .when()
                   .command(new RedeemGiftCardCommand(CARD_ID, BigDecimal.ZERO))
                   // then
                   .then()
                   .exception(IllegalArgumentException.class, "Redeem amount must be positive")
                   .noEvents();
        }

        @Test
        void rejectsRedeemingAGiftCardThatDoesNotExist() {
            // given / when / then
            fixture.given()
                   .noPriorActivity()
                   .when()
                   .command(new RedeemGiftCardCommand(CARD_ID, new BigDecimal("10.00")))
                   .then()
                   .exception(IllegalStateException.class, "Gift card does not exist")
                   .noEvents();
        }
    }
}

package io.axoniq.quickstart.giftcard.eventlog;

import java.math.BigDecimal;

/**
 * Response describing a gift card's remaining balance, computed by folding the card's events sourced directly
 * from the event store.
 *
 * <p>Unlike {@link io.axoniq.quickstart.giftcard.query.GiftCardSummary}, which is served from the read-model
 * projection, this balance is derived on the fly from the raw event history via
 * {@link GiftCardEventReader#source(String, Object, java.util.function.BiFunction)}. It demonstrates how a
 * value can be calculated from events without maintaining a dedicated projection.</p>
 *
 * @param giftCardId the unique identifier of the gift card
 * @param balance    the remaining balance, i.e. the sum of issued amounts minus redeemed amounts
 *
 * @see GiftCardEventReader
 *
 * @author AxonIQ Quickstart
 * @version 1.0
 * @since 1.0
 */
public record GiftCardBalance(String giftCardId, BigDecimal balance) {
}

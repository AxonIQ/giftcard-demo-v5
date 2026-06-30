package io.axoniq.quickstart.giftcard.web;

import io.axoniq.quickstart.giftcard.query.GiftCardSummary;

import java.util.List;

public record GiftCardStreamUpdate(
        List<GiftCardSummary> initialGiftCards,
        GiftCardSummary updatedGiftCard
) {
}

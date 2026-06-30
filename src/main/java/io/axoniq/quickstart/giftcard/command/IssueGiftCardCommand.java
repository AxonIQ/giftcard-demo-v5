package io.axoniq.quickstart.giftcard.command;

import io.axoniq.quickstart.giftcard.domain.GiftCard;
import org.axonframework.modelling.annotation.TargetEntityId;

import java.math.BigDecimal;

/**
 * Command for issuing a new gift card with an initial amount.
 *
 * <p>This command represents the intention to create a new gift card in the system.
 * It contains the unique identifier for the gift card and the initial monetary amount
 * to be loaded onto it.</p>
 *
 * <p>The command follows the CQRS pattern and is handled by the {@link GiftCard}
 * which validates the business rules before applying the corresponding {@link io.axoniq.quickstart.giftcard.event.GiftCardIssuedEvent}.</p>
 *
 * <p>Business constraints:</p>
 * <ul>
 *   <li>The gift card ID must be unique within the system</li>
 *   <li>The amount must be positive (validated by the EventSourced DCB model)</li>
 * </ul>
 *
 * @param giftCardId the unique identifier for the gift card to be created
 * @param amount the initial monetary amount to load onto the gift card (must be positive)
 *
 * @see GiftCard
 * @see io.axoniq.quickstart.giftcard.event.GiftCardIssuedEvent
 * @see <a href="https://docs.axoniq.io/reference-guide/">Axon Framework Reference Guide</a>
 *
 * @author AxonIQ Quickstart
 * @version 1.0
 * @since 1.0
 */
public record IssueGiftCardCommand(@TargetEntityId String giftCardId, BigDecimal amount) {
}
package io.axoniq.quickstart.giftcard.eventlog;

import io.axoniq.quickstart.giftcard.command.IssueGiftCardCommand;
import io.axoniq.quickstart.giftcard.command.RedeemGiftCardCommand;
import io.axoniq.quickstart.giftcard.event.GiftCardIssuedEvent;
import io.axoniq.quickstart.giftcard.event.GiftCardRedeemedEvent;
import org.axonframework.conversion.Converter;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test that exercises {@link GiftCardEventReader} against a running Axon Server instance.
 *
 * <p>Unlike {@link GiftCardEventReaderTest}, which uses an in-memory event store, this test boots the full
 * Spring application context. That context connects to the Axon Server configured for the application
 * (localhost:8124 by default), so gift card events are appended to and sourced from the real event store.</p>
 *
 * <p>The test issues and redeems a gift card through the {@link CommandGateway}, then folds the resulting
 * events straight back from the event store by their {@code giftCardId} tag to compute the remaining balance,
 * demonstrating the reader's reactive fold against real infrastructure.</p>
 *
 * <p><strong>Requires a running Axon Server.</strong> Named {@code *IT} so it is excluded from the default
 * unit-test run; execute it explicitly, e.g.
 * {@code mvn test -Dtest=GiftCardEventReaderIT -DfailIfNoTests=false}.</p>
 *
 * <p>The demo scheduler is disabled via {@code giftcard.scheduler.enabled=false} so the only gift card in
 * play is the single one this test issues and redeems. The datasource is overridden to an in-memory H2 so
 * this test - which targets the event store, not the projection - stays isolated and leaves no files on
 * disk.</p>
 */
@SpringBootTest(properties = {
        "giftcard.scheduler.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:giftcard-reader-it;DB_CLOSE_DELAY=-1"
})
class GiftCardEventReaderIT {

    @Autowired
    private CommandGateway commandGateway;

    @Autowired
    private GiftCardEventReader giftCardEventReader;

    @Autowired
    private Converter converter;

    @Test
    void foldsIssuedAndRedeemedEventsIntoBalanceFromAxonServer() {
        // given
        String giftCardId = UUID.randomUUID().toString();
        commandGateway.send(new IssueGiftCardCommand(giftCardId, new BigDecimal("100.00")))
                      .getResultMessage()
                      .orTimeout(10, TimeUnit.SECONDS)
                      .join();
        commandGateway.send(new RedeemGiftCardCommand(giftCardId, new BigDecimal("30.00")))
                      .getResultMessage()
                      .orTimeout(10, TimeUnit.SECONDS)
                      .join();

        BiFunction<BigDecimal, EventMessage, BigDecimal> balance = (total, event) ->
                switch (event.type().qualifiedName().localName()) {
                    case "GiftCardIssuedEvent" ->
                            total.add(event.payloadAs(GiftCardIssuedEvent.class, converter).amount());
                    case "GiftCardRedeemedEvent" ->
                            total.subtract(event.payloadAs(GiftCardRedeemedEvent.class, converter).amount());
                    default -> total;
                };

        // when / then
        // Sourcing happens against Axon Server; poll briefly to absorb any append/read latency.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            BigDecimal remaining = giftCardEventReader.source(giftCardId, BigDecimal.ZERO, balance)
                                                      .orTimeout(10, TimeUnit.SECONDS)
                                                      .join();
            assertThat(remaining).isEqualByComparingTo("70.00");
        });
    }
}

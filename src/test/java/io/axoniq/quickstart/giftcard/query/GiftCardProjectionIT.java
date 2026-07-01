package io.axoniq.quickstart.giftcard.query;

import io.axoniq.quickstart.giftcard.command.IssueGiftCardCommand;
import io.axoniq.quickstart.giftcard.command.RedeemGiftCardCommand;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test verifying that the disk-backed {@link GiftCardProjection} builds its read model in the H2
 * database via JDBC and serves queries from it, running against a real Axon Server instance.
 *
 * <p>The test boots the full Spring application context (which connects to Axon Server on localhost:8124 by
 * default), issues and redeems a gift card through the {@link CommandGateway}, then queries it back through the
 * {@link QueryGateway}. The projection handles the events on its streaming processor and persists the summary
 * to H2; the query handler reads it straight from the {@code gift_card_summary} table. The row is also
 * asserted directly with a {@link JdbcTemplate}.</p>
 *
 * <p>Unlike {@link io.axoniq.quickstart.giftcard.eventlog.GiftCardEventReaderIT}, this test uses the
 * application's real on-disk H2 datasource (under {@code ./data/}) to exercise durable storage. A unique gift
 * card identifier per run keeps assertions isolated from any pre-existing rows.</p>
 *
 * <p><strong>Requires a running Axon Server.</strong> Named {@code *IT} so it is excluded from the default
 * unit-test run; execute it explicitly, e.g.
 * {@code mvn test -Dtest=GiftCardProjectionIT -DfailIfNoTests=false}.</p>
 */
@SpringBootTest(properties = "giftcard.scheduler.enabled=false")
class GiftCardProjectionIT {

    @Autowired
    private CommandGateway commandGateway;

    @Autowired
    private QueryGateway queryGateway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void buildsAndServesTheReadModelFromH2() {
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

        // when / then
        // The projection is eventually consistent (streaming processor); poll until the read model catches up.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            GiftCardSummary summary = queryGateway.query(new FindGiftCardQuery(giftCardId), GiftCardSummary.class)
                                                  .orTimeout(10, TimeUnit.SECONDS)
                                                  .join();

            assertThat(summary).isNotNull();
            assertThat(summary.giftCardId()).isEqualTo(giftCardId);
            assertThat(summary.initialValue()).isEqualByComparingTo("100.00");
            assertThat(summary.remainingValue()).isEqualByComparingTo("70.00");

            // The summary is persisted to the H2 table, not just held in memory.
            BigDecimal remaining = jdbcTemplate.queryForObject(
                    "SELECT remaining_value FROM gift_card_summary WHERE gift_card_id = ?",
                    BigDecimal.class,
                    giftCardId);
            assertThat(remaining).isEqualByComparingTo("70.00");
        });
    }
}

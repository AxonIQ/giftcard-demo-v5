-- Schema for the disk-backed H2 database.
-- Run automatically by Spring Boot on startup (spring.sql.init.mode=always). Statements are idempotent
-- (CREATE TABLE IF NOT EXISTS) so they are safe across restarts of the on-disk database.

-- Axon's tracking-token table, matching the default TokenSchema used by the auto-configured JdbcTokenStore.
-- Persisting tokens lets the projection resume from its last position instead of replaying every event.
CREATE TABLE IF NOT EXISTS TokenEntry (
    processorName VARCHAR(255) NOT NULL,
    segment       INTEGER      NOT NULL,
    mask          INTEGER      NOT NULL,
    token         BLOB         NULL,
    tokenType     VARCHAR(255) NULL,
    timestamp     VARCHAR(255) NULL,
    owner         VARCHAR(255) NULL,
    PRIMARY KEY (processorName, segment)
);

-- Read model maintained by GiftCardProjection.
CREATE TABLE IF NOT EXISTS gift_card_summary (
    gift_card_id    VARCHAR(255)  NOT NULL,
    remaining_value DECIMAL(19, 2),
    initial_value   DECIMAL(19, 2),
    PRIMARY KEY (gift_card_id)
);

package app.memora.domain.model

/**
 * Explicit semantic category for a durable visual memory event.
 *
 * Designed to be extensible for future event types without architectural redesign.
 */
enum class EventType {
    DUE_DATE,
    EXPIRATION,
    BEST_BEFORE,
    WARRANTY_END,
    RETURN_DEADLINE,
    SUBSCRIPTION_END,
    OTHER
}

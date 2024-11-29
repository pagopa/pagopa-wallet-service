package it.pagopa.wallet.audit

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import it.pagopa.wallet.domain.wallets.WalletId
import java.time.Instant
import java.util.*

const val WALLET_CREATED_TYPE = "WalletCreated"
const val WALLET_LOGGING_ERROR_EVENT = "WalletLoggingError"

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = WalletCreatedEvent::class, name = WALLET_CREATED_TYPE),
    JsonSubTypes.Type(value = WalletLoggingErrorEvent::class, name = WALLET_LOGGING_ERROR_EVENT),
)
sealed interface WalletQueueEvent

/** Queue event written when wallet is created to handle wallet expiration */
data class WalletCreatedEvent(
    val eventId: String,
    val creationDate: Instant,
    val walletId: String
) : WalletQueueEvent {
    companion object {
        fun of(walletId: WalletId) =
            WalletCreatedEvent(
                eventId = UUID.randomUUID().toString(),
                creationDate = Instant.now(),
                walletId = walletId.value.toString()
            )
    }
}

/** Queue event written when an error occurs writing logging event to its collection */
data class WalletLoggingErrorEvent<T>(val eventId: String, val loggingEvent: T) :
    WalletQueueEvent where T : LoggingEvent {
    companion object {
        fun of(loggingEvent: LoggingEvent) =
            WalletLoggingErrorEvent(
                eventId = UUID.randomUUID().toString(),
                loggingEvent = loggingEvent
            )
    }
}

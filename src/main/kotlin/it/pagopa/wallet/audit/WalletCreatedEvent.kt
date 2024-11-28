package it.pagopa.wallet.audit

import it.pagopa.wallet.domain.wallets.WalletId
import java.time.Instant
import java.util.*

sealed interface WalletQueueEvent

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

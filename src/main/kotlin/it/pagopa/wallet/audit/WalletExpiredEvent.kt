package it.pagopa.wallet.audit

import it.pagopa.wallet.domain.wallets.WalletId
import java.time.OffsetDateTime
import java.util.*

sealed interface WalletEvent

data class WalletExpiredEvent(
    val eventId: String,
    val creationDate: OffsetDateTime,
    val walletId: String
) : WalletEvent {
    companion object {
        fun of(walletId: WalletId) =
            WalletExpiredEvent(
                eventId = UUID.randomUUID().toString(),
                creationDate = OffsetDateTime.now(),
                walletId = walletId.value.toString()
            )
    }
}

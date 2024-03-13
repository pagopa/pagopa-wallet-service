package it.pagopa.wallet.domain.wallets

import it.pagopa.wallet.annotations.ValueObject
import java.util.*

@ValueObject
data class WalletId(val value: UUID) {

    companion object {
        fun of(uuid: String) = WalletId(UUID.fromString(uuid))
        fun create() = WalletId(UUID.randomUUID())
    }
}

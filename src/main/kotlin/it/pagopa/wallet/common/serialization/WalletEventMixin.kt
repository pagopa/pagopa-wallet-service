package it.pagopa.wallet.common.serialization

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import it.pagopa.wallet.audit.WalletExpiredEvent
import it.pagopa.wallet.common.serialization.WalletEventMixin.Companion.WALLET_EXPIRED_TYPE

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(JsonSubTypes.Type(value = WalletExpiredEvent::class, name = WALLET_EXPIRED_TYPE))
class WalletEventMixin {
    companion object {
        const val WALLET_EXPIRED_TYPE = "WalletExpired"
    }
}

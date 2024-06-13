package it.pagopa.wallet.common.serialization

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import it.pagopa.wallet.audit.WalletCreatedEvent
import it.pagopa.wallet.common.serialization.WalletEventMixin.Companion.WALLET_CREATED_TYPE

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(JsonSubTypes.Type(value = WalletCreatedEvent::class, name = WALLET_CREATED_TYPE))
class WalletEventMixin {
    companion object {
        const val WALLET_CREATED_TYPE = "WalletCreated"
    }
}

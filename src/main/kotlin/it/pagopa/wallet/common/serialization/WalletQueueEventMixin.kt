package it.pagopa.wallet.common.serialization

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import it.pagopa.wallet.audit.*
import it.pagopa.wallet.common.serialization.WalletQueueEventMixin.Companion.SESSION_WALLET_CREATED_TYPE
import it.pagopa.wallet.common.serialization.WalletQueueEventMixin.Companion.WALLET_ADDED_TYPE
import it.pagopa.wallet.common.serialization.WalletQueueEventMixin.Companion.WALLET_APPLICATIONS_UPDATED_TYPE
import it.pagopa.wallet.common.serialization.WalletQueueEventMixin.Companion.WALLET_CREATED_TYPE
import it.pagopa.wallet.common.serialization.WalletQueueEventMixin.Companion.WALLET_DELETED_TYPE
import it.pagopa.wallet.common.serialization.WalletQueueEventMixin.Companion.WALLET_DETAILS_ADDED_TYPE
import it.pagopa.wallet.common.serialization.WalletQueueEventMixin.Companion.WALLET_MIGRATED_ADDED_TYPE
import it.pagopa.wallet.common.serialization.WalletQueueEventMixin.Companion.WALLET_ONBOARD_COMPLETED_TYPE

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = WalletCreatedEvent::class, name = WALLET_CREATED_TYPE),
    JsonSubTypes.Type(value = WalletAddedEvent::class, name = WALLET_ADDED_TYPE),
    JsonSubTypes.Type(value = WalletMigratedAddedEvent::class, name = WALLET_MIGRATED_ADDED_TYPE),
    JsonSubTypes.Type(value = WalletDeletedEvent::class, name = WALLET_DELETED_TYPE),
    JsonSubTypes.Type(value = SessionWalletCreatedEvent::class, name = SESSION_WALLET_CREATED_TYPE),
    JsonSubTypes.Type(
        value = WalletApplicationsUpdatedEvent::class,
        name = WALLET_APPLICATIONS_UPDATED_TYPE
    ),
    JsonSubTypes.Type(value = WalletDetailsAddedEvent::class, name = WALLET_DETAILS_ADDED_TYPE),
    JsonSubTypes.Type(
        value = WalletOnboardCompletedEvent::class,
        name = WALLET_ONBOARD_COMPLETED_TYPE
    ),
)
class WalletQueueEventMixin {
    companion object {
        const val WALLET_CREATED_TYPE = "WalletCreated"
        const val WALLET_ADDED_TYPE = "WalletAdded"
        const val WALLET_MIGRATED_ADDED_TYPE = "WalletMigratedAdded"
        const val WALLET_DELETED_TYPE = "WalletDeleted"
        const val SESSION_WALLET_CREATED_TYPE = "SessionWalletCreated"
        const val WALLET_APPLICATIONS_UPDATED_TYPE = "WalletApplicationsUpdated"
        const val WALLET_DETAILS_ADDED_TYPE = "WalletDetailsAdded"
        const val WALLET_ONBOARD_COMPLETED_TYPE = "WalletOnboardCompleted"
    }
}

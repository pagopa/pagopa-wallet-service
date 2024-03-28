package it.pagopa.wallet.domain.wallets

import it.pagopa.wallet.annotations.ValueObject
import java.time.Instant

@ValueObject
data class WalletApplication(
    val id: WalletApplicationId,
    val status: WalletApplicationStatus,
    val creationDate: Instant,
    val updateDate: Instant,
    val metadata: WalletApplicationMetadata
) {
    fun addMetadata(key: WalletApplicationMetadata.Metadata, value: String): WalletApplication =
        copy(metadata = metadata + (key to value))
}

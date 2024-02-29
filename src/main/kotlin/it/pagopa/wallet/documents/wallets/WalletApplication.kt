package it.pagopa.wallet.documents.wallets

import it.pagopa.wallet.domain.wallets.WalletApplication
import it.pagopa.wallet.domain.wallets.WalletApplicationId
import it.pagopa.wallet.domain.wallets.WalletApplicationMetadata
import it.pagopa.wallet.domain.wallets.WalletApplicationStatus
import java.time.Instant

data class WalletApplication(
    val id: String,
    val status: String,
    val creationDate: String,
    val updateDate: String,
    val metadata: Map<String, String>
) {
    fun toDomain() =
        WalletApplication(
            WalletApplicationId(id),
            WalletApplicationStatus.valueOf(status),
            Instant.parse(creationDate),
            Instant.parse(updateDate),
            WalletApplicationMetadata(metadata)
        )
}

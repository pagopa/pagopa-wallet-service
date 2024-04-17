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
)

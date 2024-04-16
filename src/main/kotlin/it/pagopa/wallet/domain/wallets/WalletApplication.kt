package it.pagopa.wallet.domain.wallets

import io.vavr.control.Try
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
    fun lastUsageIO(): Instant? {
        return metadata.data
            .getOrDefault(WalletApplicationMetadata.Metadata.LAST_USED_IO, null)
            ?.let { Try.of { Instant.parse(it) } }
            ?.orNull
    }
}

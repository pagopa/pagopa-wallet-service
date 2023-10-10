package it.pagopa.wallet.documents.wallets

import it.pagopa.wallet.domain.services.ServiceId
import it.pagopa.wallet.domain.services.ServiceName
import it.pagopa.wallet.domain.services.ServiceStatus
import it.pagopa.wallet.domain.wallets.WalletService
import java.time.Instant
import java.util.*

data class WalletService(
    val id: String,
    val name: String,
    val status: String,
    val lastUpdateDate: String
) {
    fun toDomain() =
        WalletService(
            ServiceId(UUID.fromString(id)),
            ServiceName(name),
            ServiceStatus.valueOf(status),
            Instant.parse(lastUpdateDate)
        )
}

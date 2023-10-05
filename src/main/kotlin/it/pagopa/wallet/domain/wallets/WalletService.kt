package it.pagopa.wallet.domain.wallets

import it.pagopa.wallet.domain.common.ServiceId
import it.pagopa.wallet.domain.common.ServiceName
import it.pagopa.wallet.domain.common.ServiceStatus
import java.time.Instant

data class WalletService(
    val id: ServiceId,
    val name: ServiceName,
    val status: ServiceStatus,
    val lastUpdate: Instant
)

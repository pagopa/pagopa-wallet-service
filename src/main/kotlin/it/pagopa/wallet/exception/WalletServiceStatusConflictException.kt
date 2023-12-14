package it.pagopa.wallet.exception

import it.pagopa.wallet.domain.services.ServiceName
import it.pagopa.wallet.domain.services.ServiceStatus

class WalletServiceStatusConflictException(
    val updatedServices: Map<ServiceName, ServiceStatus>,
    val failedServices: Map<ServiceName, ServiceStatus>
) :
    RuntimeException(
        "Wallet services update failed, could not update services ${failedServices.keys}"
    )

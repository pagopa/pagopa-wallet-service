package it.pagopa.wallet.exception

import it.pagopa.wallet.domain.applications.ApplicationId
import it.pagopa.wallet.domain.applications.ApplicationStatus

class WalletServiceStatusConflictException(
    val updatedServices: Map<ApplicationId, ApplicationStatus>,
    val failedServices: Map<ApplicationId, ApplicationStatus>
) :
    RuntimeException(
        "Wallet services update failed, could not update services ${failedServices.keys}"
    )

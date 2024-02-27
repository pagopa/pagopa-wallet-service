package it.pagopa.wallet.exception

import it.pagopa.wallet.domain.wallets.WalletApplicationId
import it.pagopa.wallet.domain.wallets.WalletApplicationStatus

class WalletApplicationStatusConflictException(
    val updatedApplications: Map<WalletApplicationId, WalletApplicationStatus>,
    val failedApplications: Map<WalletApplicationId, WalletApplicationStatus>
) :
    RuntimeException(
        "Wallet application update failed, could not update applications ${failedApplications.keys}"
    )

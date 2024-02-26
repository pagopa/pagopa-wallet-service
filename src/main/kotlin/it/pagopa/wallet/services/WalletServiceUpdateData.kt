package it.pagopa.wallet.services

import it.pagopa.wallet.documents.wallets.Wallet
import it.pagopa.wallet.domain.wallets.WalletApplicationId
import it.pagopa.wallet.domain.wallets.WalletApplicationStatus

data class WalletServiceUpdateData(
    val successfullyUpdatedApplications: Map<WalletApplicationId, WalletApplicationStatus>,
    val applicationsWithUpdateFailed: Map<WalletApplicationId, WalletApplicationStatus>,
    val updatedWallet: Wallet
)

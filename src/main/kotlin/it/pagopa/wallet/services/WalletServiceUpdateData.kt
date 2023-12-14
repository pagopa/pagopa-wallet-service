package it.pagopa.wallet.services

import it.pagopa.wallet.documents.wallets.Wallet
import it.pagopa.wallet.domain.services.ServiceName
import it.pagopa.wallet.domain.services.ServiceStatus

data class WalletServiceUpdateData(
    val successfullyUpdatedServices: Map<ServiceName, ServiceStatus>,
    val servicesWithUpdateFailed: Map<ServiceName, ServiceStatus>,
    val updatedWallet: Wallet
)

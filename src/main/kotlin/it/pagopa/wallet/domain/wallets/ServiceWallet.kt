package it.pagopa.wallet.domain.wallets

import it.pagopa.wallet.domain.services.ServiceStatus

data class ServiceWallet(val serviceName: String, val status: ServiceStatus)

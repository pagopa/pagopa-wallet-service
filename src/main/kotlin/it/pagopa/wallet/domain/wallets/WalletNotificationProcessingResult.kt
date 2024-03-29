package it.pagopa.wallet.domain.wallets

import it.pagopa.generated.wallet.model.WalletStatusDto

data class WalletNotificationProcessingResult(
    val newWalletStatus: WalletStatusDto,
    val walletDetails: it.pagopa.wallet.domain.wallets.details.WalletDetails<*>?,
    val errorCode: String?
)

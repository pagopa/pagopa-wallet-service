package it.pagopa.wallet.exception

import it.pagopa.wallet.domain.wallets.WalletId
import org.springframework.http.HttpStatus

class WalletAlreadyDeletedException(val walletId: WalletId) :
    ApiError("Wallet with walletId [${walletId.value}] already deleted") {
    override fun toRestException(): RestApiException =
        RestApiException(HttpStatus.NO_CONTENT, "Already Deleted", message!!)
}

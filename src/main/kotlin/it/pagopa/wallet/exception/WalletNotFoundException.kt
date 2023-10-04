package it.pagopa.wallet.exception

import it.pagopa.wallet.domain.wallets.WalletId
import org.springframework.http.HttpStatus

class WalletNotFoundException(walletId: WalletId) :
    ApiError("Cannot find wallet with id $walletId") {
    override fun toRestException(): RestApiException =
        RestApiException(HttpStatus.NOT_FOUND, "Wallet not found", message!!)
}

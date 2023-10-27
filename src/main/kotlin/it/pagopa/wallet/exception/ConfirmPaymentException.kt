package it.pagopa.wallet.exception

import it.pagopa.wallet.domain.wallets.WalletId
import org.springframework.http.HttpStatus

class ConfirmPaymentException(walletId: WalletId) :
    ApiError("Confirm Payment didn't get right state for wallet with id $walletId") {
    override fun toRestException(): RestApiException =
        RestApiException(HttpStatus.CONFLICT, "Wrong state for confirm payment", message!!)
}

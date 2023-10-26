package it.pagopa.wallet.exception

import it.pagopa.wallet.domain.wallets.WalletId
import org.springframework.http.HttpStatus

class ConfirmPaymentException(walletId: WalletId) :
    ApiError("Confirm Payment didn't get right state for wallet with id $walletId") {
    override fun toRestException(): RestApiException =
        RestApiException(HttpStatus.CONFLICT, "Wallet and session mismatch", message!!)
}

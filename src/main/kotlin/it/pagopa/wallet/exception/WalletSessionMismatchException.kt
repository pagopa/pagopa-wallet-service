package it.pagopa.wallet.exception

import it.pagopa.wallet.domain.wallets.WalletId
import org.springframework.http.HttpStatus

class WalletSessionMismatchException(sessionId: String, walletId: WalletId) :
    ApiError("Cannot find wallet with id $walletId mapped with session $sessionId") {
    override fun toRestException(): RestApiException =
        RestApiException(HttpStatus.CONFLICT, "Wallet and session mismatch", message!!)
}

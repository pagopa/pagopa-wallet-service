package it.pagopa.wallet.exception

import it.pagopa.wallet.domain.wallets.WalletId
import org.springframework.http.HttpStatus

class WalletSessionMismatchException(sessionId: String, walletId: WalletId) :
    ApiError("Cannot find wallet with id $walletId by session $sessionId") {
    override fun toRestException(): RestApiException =
        RestApiException(HttpStatus.CONFLICT, "Wallet id doesn't match session", message!!)
}

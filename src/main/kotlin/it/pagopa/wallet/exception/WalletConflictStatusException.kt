package it.pagopa.wallet.exception

import it.pagopa.wallet.domain.wallets.WalletId
import org.springframework.http.HttpStatus

class WalletConflictStatusException(walletId: WalletId) :
    ApiError("Conflict with the current state walletId $walletId") {
    override fun toRestException(): RestApiException =
        RestApiException(HttpStatus.CONFLICT, "Conflict", message!!)
}

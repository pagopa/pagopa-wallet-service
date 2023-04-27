package it.pagopa.wallet.exception

import java.util.*
import org.springframework.http.HttpStatus

class WalletNotFoundException(walletId: UUID) : ApiError("Cannot find wallet with id $walletId") {
    override fun toRestException(): RestApiException =
        RestApiException(HttpStatus.NOT_FOUND, "Wallet not found", message!!)
}

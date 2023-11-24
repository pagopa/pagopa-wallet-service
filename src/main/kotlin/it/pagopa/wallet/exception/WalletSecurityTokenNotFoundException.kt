package it.pagopa.wallet.exception

import org.springframework.http.HttpStatus

class WalletSecurityTokenNotFoundException() : ApiError("Unauthorised") {
    override fun toRestException(): RestApiException =
        RestApiException(HttpStatus.UNAUTHORIZED, "Unauthorised action", message!!)
}

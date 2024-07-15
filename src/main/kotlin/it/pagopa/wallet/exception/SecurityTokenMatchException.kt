package it.pagopa.wallet.exception

import org.springframework.http.HttpStatus

class SecurityTokenMatchException : ApiError("Cannot match Security token") {
    override fun toRestException(): RestApiException =
        RestApiException(HttpStatus.UNAUTHORIZED, "Security token match failed", message!!)
}

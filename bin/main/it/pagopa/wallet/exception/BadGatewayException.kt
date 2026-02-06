package it.pagopa.wallet.exception

import org.springframework.http.HttpStatus

class BadGatewayException(message: String?) : ApiError(message) {
    override fun toRestException(): RestApiException =
        RestApiException(HttpStatus.BAD_GATEWAY, "Bad Gateway", message ?: "")
}

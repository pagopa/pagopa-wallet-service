package it.pagopa.wallet.exception

import org.springframework.http.HttpStatus

class InternalServerErrorException(message: String?) : ApiError(message) {
    override fun toRestException(): RestApiException =
        RestApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", message ?: "")
}

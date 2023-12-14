package it.pagopa.wallet.exception

import org.springframework.http.HttpStatus

class InvalidRequestException(private val description: String) : ApiError(description) {
    override fun toRestException() =
        RestApiException(
            httpStatus = HttpStatus.BAD_REQUEST,
            description = description,
            title = "Input request is invalid"
        )
}

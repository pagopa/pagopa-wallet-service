package it.pagopa.wallet.exception

import org.springframework.http.HttpStatus

/**
 * Exception class wrapping checked exceptions that can occur during jwt generation service
 * invocation
 *
 * @see it.pagopa.wallet.client.JwtTokenIssuerClient
 */
class JWTTokenGenerationException(
    private val description: String,
    private val httpStatus: HttpStatus
) : ApiError(description) {
    override fun toRestException() =
        RestApiException(
            httpStatus = httpStatus,
            description = description,
            title = "Jwt Issuer Invocation exception"
        )
}

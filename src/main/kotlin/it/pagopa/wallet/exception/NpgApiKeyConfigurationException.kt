package it.pagopa.wallet.exception

import org.springframework.http.HttpStatus

/** Exception thrown when NPG per PSP api key configuration cannot be successfully parsed */
open class NpgApiKeyConfigurationException(
    message: String,
) : ApiError("Npg api key configuration error: $message") {
    override fun toRestException(): RestApiException =
        RestApiException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase,
            message!!
        )
}

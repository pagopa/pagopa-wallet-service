package it.pagopa.wallet.exception

import org.springframework.http.HttpStatus

class PDVTokenizerException(
    private val description: String,
    private val httpStatusCode: HttpStatus
) : ApiError(description) {
    override fun toRestException() =
        RestApiException(
            httpStatus = httpStatusCode, description = description, title = "PDV Tokenizer Error")
}

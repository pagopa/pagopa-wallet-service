package it.pagopa.wallet.exception

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class PDVTokenizerExceptionTest {

    @Test
    fun `Should create PDVTokenizerException with correct description and httpStatusCode`() {
        val description = "Error communicating with PDV tokenizer"
        val httpStatusCode = HttpStatus.INTERNAL_SERVER_ERROR

        val exception = PDVTokenizerException(description, httpStatusCode)

        assertEquals(description, exception.message)
    }

    @Test
    fun `Should convert to RestApiException with correct parameters`() {
        val description = "PDV service unavailable"
        val httpStatusCode = HttpStatus.BAD_GATEWAY
        val exception = PDVTokenizerException(description, httpStatusCode)

        val restApiException = exception.toRestException()

        assertEquals(httpStatusCode, restApiException.httpStatus)
        assertEquals("PDV Tokenizer Error", restApiException.title)
        assertEquals(description, restApiException.description)
    }

    @Test
    fun `Should convert to RestApiException with 500 status code`() {
        val description = "Internal error in PDV tokenizer"
        val httpStatusCode = HttpStatus.INTERNAL_SERVER_ERROR
        val exception = PDVTokenizerException(description, httpStatusCode)

        val restApiException = exception.toRestException()

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, restApiException.httpStatus)
        assertEquals("PDV Tokenizer Error", restApiException.title)
        assertEquals(description, restApiException.description)
    }

    @Test
    fun `Should convert to RestApiException with 400 status code`() {
        val description = "Invalid request to PDV tokenizer"
        val httpStatusCode = HttpStatus.BAD_REQUEST
        val exception = PDVTokenizerException(description, httpStatusCode)

        val restApiException = exception.toRestException()

        assertEquals(HttpStatus.BAD_REQUEST, restApiException.httpStatus)
        assertEquals("PDV Tokenizer Error", restApiException.title)
        assertEquals(description, restApiException.description)
    }

    @Test
    fun `Should convert to RestApiException with 404 status code`() {
        val description = "PDV tokenizer resource not found"
        val httpStatusCode = HttpStatus.NOT_FOUND
        val exception = PDVTokenizerException(description, httpStatusCode)

        val restApiException = exception.toRestException()

        assertEquals(HttpStatus.NOT_FOUND, restApiException.httpStatus)
        assertEquals("PDV Tokenizer Error", restApiException.title)
        assertEquals(description, restApiException.description)
    }
}

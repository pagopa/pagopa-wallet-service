package it.pagopa.wallet.exceptionhandler

import it.pagopa.wallet.exception.NpgClientException
import it.pagopa.wallet.exception.RestApiException
import jakarta.xml.bind.ValidationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class ExceptionHandlerTest {

    private val exceptionHandler = ExceptionHandler()

    @Test
    fun `Should handle RestApiException`() {
        val response = exceptionHandler.handleException(
            RestApiException(
                httpStatus = HttpStatus.UNAUTHORIZED,
                title = "title",
                description = "description"
            )
        )
        assertEquals("Error processing request: title - description", response.body)
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `Should handle ApiError`() {
        val exception = NpgClientException(
            httpStatusCode = HttpStatus.UNAUTHORIZED,
            description = "description"
        )
        val response = exceptionHandler.handleException(
            exception
        )
        assertEquals(
            "Error processing request: ${exception.toRestException().title} - ${exception.toRestException().description}",
            response.body
        )
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `Should handle ValidationExceptions`() {
        val exception = ValidationException("Invalid request")
        val response = exceptionHandler.handleRequestValidationException(
            exception
        )
        assertEquals(
            "Error processing request: ${exception.localizedMessage}",
            response.body
        )
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }
}
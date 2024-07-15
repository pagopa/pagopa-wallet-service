package it.pagopa.wallet.exceptionhandler

import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.common.tracing.TracingUtilsTest
import it.pagopa.wallet.common.tracing.WalletTracing
import it.pagopa.wallet.exception.NpgClientException
import it.pagopa.wallet.exception.RestApiException
import jakarta.xml.bind.ValidationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.springframework.http.HttpStatus
import org.springframework.web.server.ServerWebExchange

class ExceptionHandlerTest {

    private val walletTracing = spy(WalletTracing(TracingUtilsTest.getMock()))

    private val exceptionHandler = ExceptionHandler(walletTracing)

    @Test
    fun `Should handle RestApiException`() {
        val response =
            exceptionHandler.handleException(
                RestApiException(
                    httpStatus = HttpStatus.UNAUTHORIZED,
                    title = "title",
                    description = "description"
                )
            )
        assertEquals(
            WalletTestUtils.buildProblemJson(
                httpStatus = HttpStatus.UNAUTHORIZED,
                title = "title",
                description = "description"
            ),
            response.body
        )
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `Should handle ApiError`() {
        val exception =
            NpgClientException(
                httpStatusCode = HttpStatus.UNAUTHORIZED,
                description = "description"
            )
        val response = exceptionHandler.handleException(exception)
        assertEquals(
            WalletTestUtils.buildProblemJson(
                httpStatus = HttpStatus.UNAUTHORIZED,
                title = "Npg Invocation exception",
                description = "description"
            ),
            response.body
        )
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `Should handle ValidationExceptions`() {
        val exception = ValidationException("Invalid request")
        val webExchange = mock<ServerWebExchange>()
        val response = exceptionHandler.handleRequestValidationException(exception, webExchange)
        assertEquals(
            WalletTestUtils.buildProblemJson(
                httpStatus = HttpStatus.BAD_REQUEST,
                title = "Bad request",
                description = "Input request is not valid"
            ),
            response.body
        )
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `Should handle generic exception`() {
        val exception = NullPointerException("Nullpointer exception")
        val response = exceptionHandler.handleGenericException(exception)
        assertEquals(
            WalletTestUtils.buildProblemJson(
                httpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
                title = "Error processing the request",
                description = "An internal error occurred processing the request"
            ),
            response.body
        )
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
    }
}

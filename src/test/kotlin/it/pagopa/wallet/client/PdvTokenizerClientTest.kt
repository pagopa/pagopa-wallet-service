package it.pagopa.wallet.client

import it.pagopa.generated.pdv.api.TokenApi
import it.pagopa.generated.pdv.model.PiiResource
import it.pagopa.generated.pdv.model.TokenResource
import it.pagopa.wallet.exception.PDVTokenizerException
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class PdvTokenizerClientTest {

    private val pdvTokenizerApi: TokenApi = mock()
    private val pdvTokenizerClient = PdvTokenizerClient(pdvTokenizerApi)

    @Test
    fun `should tokenize successfully`() {
        val pii = "pii-value"
        val expectedToken = UUID.randomUUID()
        val tokenResource = TokenResource().token(expectedToken)

        given { pdvTokenizerApi.saveUsingPUT(any()) }.willReturn(Mono.just(tokenResource))

        StepVerifier.create(pdvTokenizerClient.tokenize(pii))
            .assertNext { result -> assertEquals(expectedToken, result.token) }
            .verifyComplete()

        argumentCaptor<PiiResource> {
            verify(pdvTokenizerApi).saveUsingPUT(capture())
            assertEquals(pii, firstValue.pii)
        }
    }

    @Test
    fun `should throw PDVTokenizerException with BAD_GATEWAY when 400 Bad Request`() {
        val pii = "pii-value"

        val exception =
            WebClientResponseException.create(
                HttpStatus.BAD_REQUEST.value(), "Bad Request", null, null, null)

        given { pdvTokenizerApi.saveUsingPUT(any()) }.willReturn(Mono.error(exception))

        StepVerifier.create(pdvTokenizerClient.tokenize(pii))
            .expectErrorMatches { ex ->
                assertTrue(ex is PDVTokenizerException)
                val pdvEx = ex as PDVTokenizerException
                // Use toRestException() to verify the private properties
                val restEx = pdvEx.toRestException()

                assertEquals(HttpStatus.BAD_GATEWAY, restEx.httpStatus)
                assertEquals("EcommercePaymentMethods - Bad request", restEx.description)
                true
            }
            .verify()
    }

    @Test
    fun `should throw PDVTokenizerException with INTERNAL_SERVER_ERROR when 401 Unauthorized`() {
        val pii = "pii-value"

        val exception =
            WebClientResponseException.create(
                HttpStatus.UNAUTHORIZED.value(), "Unauthorized", null, null, null)

        given { pdvTokenizerApi.saveUsingPUT(any()) }.willReturn(Mono.error(exception))

        StepVerifier.create(pdvTokenizerClient.tokenize(pii))
            .expectErrorMatches { ex ->
                assertTrue(ex is PDVTokenizerException)
                val pdvEx = ex as PDVTokenizerException
                val restEx = pdvEx.toRestException()

                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, restEx.httpStatus)
                assertEquals(
                    "EcommercePaymentMethods - Misconfigured EcommercePaymentMethods api key",
                    restEx.description)
                true
            }
            .verify()
    }

    @Test
    fun `should throw PDVTokenizerException with BAD_GATEWAY when 500 Internal Server Error`() {
        val pii = "pii-value"

        val exception =
            WebClientResponseException.create(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error", null, null, null)

        given { pdvTokenizerApi.saveUsingPUT(any()) }.willReturn(Mono.error(exception))

        StepVerifier.create(pdvTokenizerClient.tokenize(pii))
            .expectErrorMatches { ex ->
                assertTrue(ex is PDVTokenizerException)
                val pdvEx = ex as PDVTokenizerException
                val restEx = pdvEx.toRestException()

                assertEquals(HttpStatus.BAD_GATEWAY, restEx.httpStatus)
                assertEquals("EcommercePaymentMethods - internal server error", restEx.description)
                true
            }
            .verify()
    }

    @Test
    fun `should throw PDVTokenizerException with BAD_GATEWAY when other errors`() {
        val pii = "pii-value"
        val status = HttpStatus.NOT_FOUND

        val exception =
            WebClientResponseException.create(status.value(), "Not Found", null, null, null)

        given { pdvTokenizerApi.saveUsingPUT(any()) }.willReturn(Mono.error(exception))

        StepVerifier.create(pdvTokenizerClient.tokenize(pii))
            .expectErrorMatches { ex ->
                assertTrue(ex is PDVTokenizerException)
                val pdvEx = ex as PDVTokenizerException
                val restEx = pdvEx.toRestException()

                assertEquals(HttpStatus.BAD_GATEWAY, restEx.httpStatus)
                assertEquals("EcommercePaymentMethods - server error: $status", restEx.description)
                true
            }
            .verify()
    }
}

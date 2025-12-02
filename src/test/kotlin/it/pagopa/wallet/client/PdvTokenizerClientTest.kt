package it.pagopa.wallet.client

import it.pagopa.generated.pdv.api.TokenApi
import it.pagopa.generated.pdv.model.PiiResource
import it.pagopa.generated.pdv.model.TokenResource
import it.pagopa.wallet.exception.PDVTokenizerException
import it.pagopa.wallet.repositories.PdvTokenCacheDocument
import it.pagopa.wallet.repositories.PdvTokenTemplateWrapper
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
    private val pdvTokenRedisTemplate: PdvTokenTemplateWrapper = mock()
    private val pdvTokenizerClient = PdvTokenizerClient(pdvTokenizerApi, pdvTokenRedisTemplate)

    @Test
    fun `should return cached token when cache hit`() {
        val fiscalCode = "RSSMRA80A01H501U"
        val normalizedFiscalCode = fiscalCode.uppercase()
        val cachedToken = UUID.randomUUID()
        val cachedDocument = PdvTokenCacheDocument(normalizedFiscalCode, cachedToken)

        given { pdvTokenRedisTemplate.findById(normalizedFiscalCode) }
            .willReturn(Mono.just(cachedDocument))

        StepVerifier.create(pdvTokenizerClient.tokenize(fiscalCode))
            .assertNext { result -> assertEquals(cachedToken, result.token) }
            .verifyComplete()

        verify(pdvTokenRedisTemplate, times(1)).findById(normalizedFiscalCode)
        verify(pdvTokenizerApi, never()).saveUsingPUT(any())
    }

    @Test
    fun `should call PDV and cache result when cache miss`() {
        val fiscalCode = "RSSMRA80A01H501U"
        val normalizedFiscalCode = fiscalCode.uppercase()
        val pdvToken = UUID.randomUUID()
        val tokenResource = TokenResource().token(pdvToken)

        given { pdvTokenRedisTemplate.findById(normalizedFiscalCode) }.willReturn(Mono.empty())
        given { pdvTokenizerApi.saveUsingPUT(any()) }.willReturn(Mono.just(tokenResource))
        given { pdvTokenRedisTemplate.save(any()) }.willReturn(Mono.just(true))

        StepVerifier.create(pdvTokenizerClient.tokenize(fiscalCode))
            .assertNext { result -> assertEquals(pdvToken, result.token) }
            .verifyComplete()

        verify(pdvTokenRedisTemplate, times(1)).findById(normalizedFiscalCode)
        verify(pdvTokenizerApi, times(1)).saveUsingPUT(any())
        argumentCaptor<PdvTokenCacheDocument> {
            verify(pdvTokenRedisTemplate, times(1)).save(capture())
            assertEquals(normalizedFiscalCode, firstValue.fiscalCode)
            assertEquals(pdvToken, firstValue.token)
        }
    }

    @Test
    fun `should fallback to PDV when cache read fails`() {
        val fiscalCode = "RSSMRA80A01H501U"
        val normalizedFiscalCode = fiscalCode.uppercase()
        val pdvToken = UUID.randomUUID()
        val tokenResource = TokenResource().token(pdvToken)

        given { pdvTokenRedisTemplate.findById(normalizedFiscalCode) }
            .willReturn(Mono.error(RuntimeException("Redis connection failed")))
        given { pdvTokenizerApi.saveUsingPUT(any()) }.willReturn(Mono.just(tokenResource))
        given { pdvTokenRedisTemplate.save(any()) }.willReturn(Mono.just(true))

        StepVerifier.create(pdvTokenizerClient.tokenize(fiscalCode))
            .assertNext { result -> assertEquals(pdvToken, result.token) }
            .verifyComplete()

        verify(pdvTokenRedisTemplate, times(1)).findById(normalizedFiscalCode)
        verify(pdvTokenizerApi, times(1)).saveUsingPUT(any())
        verify(pdvTokenRedisTemplate, times(1)).save(any())
    }

    @Test
    fun `should continue when cache write fails`() {
        val fiscalCode = "RSSMRA80A01H501U"
        val normalizedFiscalCode = fiscalCode.uppercase()
        val pdvToken = UUID.randomUUID()
        val tokenResource = TokenResource().token(pdvToken)

        given { pdvTokenRedisTemplate.findById(normalizedFiscalCode) }.willReturn(Mono.empty())
        given { pdvTokenizerApi.saveUsingPUT(any()) }.willReturn(Mono.just(tokenResource))
        given { pdvTokenRedisTemplate.save(any()) }
            .willReturn(Mono.error(RuntimeException("Redis write failed")))

        StepVerifier.create(pdvTokenizerClient.tokenize(fiscalCode))
            .assertNext { result -> assertEquals(pdvToken, result.token) }
            .verifyComplete()

        verify(pdvTokenRedisTemplate, times(1)).findById(normalizedFiscalCode)
        verify(pdvTokenizerApi, times(1)).saveUsingPUT(any())
        verify(pdvTokenRedisTemplate, times(1)).save(any())
    }

    @Test
    fun `should tokenize successfully without cache when PDV succeeds`() {
        val fiscalCode = "RSSMRA80A01H501U"
        val normalizedFiscalCode = fiscalCode.uppercase()
        val expectedToken = UUID.randomUUID()
        val tokenResource = TokenResource().token(expectedToken)

        given { pdvTokenRedisTemplate.findById(normalizedFiscalCode) }.willReturn(Mono.empty())
        given { pdvTokenizerApi.saveUsingPUT(any()) }.willReturn(Mono.just(tokenResource))
        given { pdvTokenRedisTemplate.save(any()) }.willReturn(Mono.just(true))

        StepVerifier.create(pdvTokenizerClient.tokenize(fiscalCode))
            .assertNext { result -> assertEquals(expectedToken, result.token) }
            .verifyComplete()

        argumentCaptor<PiiResource> {
            verify(pdvTokenizerApi, times(1)).saveUsingPUT(capture())
            assertEquals(fiscalCode, firstValue.pii)
        }
    }

    @Test
    fun `should throw PDVTokenizerException with BAD_GATEWAY when 400 Bad Request`() {
        val fiscalCode = "RSSMRA80A01H501U"
        val normalizedFiscalCode = fiscalCode.uppercase()

        val exception =
            WebClientResponseException.create(
                HttpStatus.BAD_REQUEST.value(), "Bad Request", null, null, null)

        given { pdvTokenRedisTemplate.findById(normalizedFiscalCode) }.willReturn(Mono.empty())
        given { pdvTokenizerApi.saveUsingPUT(any()) }.willReturn(Mono.error(exception))

        StepVerifier.create(pdvTokenizerClient.tokenize(fiscalCode))
            .expectErrorMatches { ex ->
                assertTrue(ex is PDVTokenizerException)
                val pdvEx = ex as PDVTokenizerException
                val restEx = pdvEx.toRestException()

                assertEquals(HttpStatus.BAD_GATEWAY, restEx.httpStatus)
                assertEquals("PDV - Bad request response", restEx.description)
                true
            }
            .verify()
    }

    @Test
    fun `should throw PDVTokenizerException with INTERNAL_SERVER_ERROR when 401 Unauthorized`() {
        val fiscalCode = "RSSMRA80A01H501U"
        val normalizedFiscalCode = fiscalCode.uppercase()

        val exception =
            WebClientResponseException.create(
                HttpStatus.UNAUTHORIZED.value(), "Unauthorized", null, null, null)

        given { pdvTokenRedisTemplate.findById(normalizedFiscalCode) }.willReturn(Mono.empty())
        given { pdvTokenizerApi.saveUsingPUT(any()) }.willReturn(Mono.error(exception))

        StepVerifier.create(pdvTokenizerClient.tokenize(fiscalCode))
            .expectErrorMatches { ex ->
                assertTrue(ex is PDVTokenizerException)
                val pdvEx = ex as PDVTokenizerException
                val restEx = pdvEx.toRestException()

                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, restEx.httpStatus)
                assertEquals("PDV - Misconfigured PDV api key", restEx.description)
                true
            }
            .verify()
    }

    @Test
    fun `should throw PDVTokenizerException with BAD_GATEWAY when 500 Internal Server Error`() {
        val fiscalCode = "RSSMRA80A01H501U"
        val normalizedFiscalCode = fiscalCode.uppercase()

        val exception =
            WebClientResponseException.create(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error", null, null, null)

        given { pdvTokenRedisTemplate.findById(normalizedFiscalCode) }.willReturn(Mono.empty())
        given { pdvTokenizerApi.saveUsingPUT(any()) }.willReturn(Mono.error(exception))

        StepVerifier.create(pdvTokenizerClient.tokenize(fiscalCode))
            .expectErrorMatches { ex ->
                assertTrue(ex is PDVTokenizerException)
                val pdvEx = ex as PDVTokenizerException
                val restEx = pdvEx.toRestException()

                assertEquals(HttpStatus.BAD_GATEWAY, restEx.httpStatus)
                assertEquals("PDV - internal server error", restEx.description)
                true
            }
            .verify()
    }

    @Test
    fun `should throw PDVTokenizerException with BAD_GATEWAY when other errors`() {
        val fiscalCode = "RSSMRA80A01H501U"
        val normalizedFiscalCode = fiscalCode.uppercase()
        val status = HttpStatus.NOT_FOUND

        val exception =
            WebClientResponseException.create(status.value(), "Not Found", null, null, null)

        given { pdvTokenRedisTemplate.findById(normalizedFiscalCode) }.willReturn(Mono.empty())
        given { pdvTokenizerApi.saveUsingPUT(any()) }.willReturn(Mono.error(exception))

        StepVerifier.create(pdvTokenizerClient.tokenize(fiscalCode))
            .expectErrorMatches { ex ->
                assertTrue(ex is PDVTokenizerException)
                val pdvEx = ex as PDVTokenizerException
                val restEx = pdvEx.toRestException()

                assertEquals(HttpStatus.BAD_GATEWAY, restEx.httpStatus)
                assertEquals("PDV - server error: $status", restEx.description)
                true
            }
            .verify()
    }

    @Test
    fun `should normalize fiscal code consistently for cache key`() {
        val fiscalCode = "RSSMRA80A01H501U"
        val normalizedFiscalCode = fiscalCode.uppercase()
        val cachedToken = UUID.randomUUID()
        val cachedDocument = PdvTokenCacheDocument(normalizedFiscalCode, cachedToken)

        given { pdvTokenRedisTemplate.findById(normalizedFiscalCode) }
            .willReturn(Mono.just(cachedDocument))

        StepVerifier.create(pdvTokenizerClient.tokenize(fiscalCode))
            .expectNextCount(1)
            .verifyComplete()

        // check same normalized fiscal code is used
        verify(pdvTokenRedisTemplate, times(1)).findById(normalizedFiscalCode)
    }
}

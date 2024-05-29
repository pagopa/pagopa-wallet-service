package it.pagopa.wallet.client

import it.pagopa.generated.afm.api.CalculatorApi
import it.pagopa.generated.afm.model.BundleOption
import it.pagopa.generated.afm.model.Transfer
import it.pagopa.wallet.exception.RestApiException
import java.util.*
import java.util.stream.Stream
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import reactor.kotlin.test.test

class AfmCalculatorClientTest {

    private val afmCalculatorApi: CalculatorApi = mock()
    lateinit var afmCalculatorClient: AfmCalculatorClient

    @BeforeEach
    fun setup() {
        reset(afmCalculatorApi)
        afmCalculatorClient = AfmCalculatorClient(afmCalculatorApi)
    }

    @Test
    fun `Calculate fees with stub request must return the psp bundle`() {
        given { afmCalculatorApi.getFeesMulti(any(), any(), any()) }
            .willReturn(generateBundle().toMono())

        afmCalculatorClient
            .getPspDetails(PSP_ID, "AFM")
            .test()
            .expectNextMatches { it.idPsp == PSP_ID && it.pspBusinessName == PSP_BUSINESS_NAME }
            .verifyComplete()
    }

    @Test
    fun `Getting psp details for non existing psp must return an empty mono`() {
        given { afmCalculatorApi.getFeesMulti(any(), any(), any()) }
            .willReturn(generateBundle().toMono())

        afmCalculatorClient.getPspDetails("nonExistingPsp", "AFM").test().verifyComplete()
    }

    @ParameterizedTest
    @MethodSource("errorResponses")
    fun `Should return error when afm request fails`(response: WebClientResponseException) {
        given { afmCalculatorApi.getFeesMulti(any(), any(), any()) }
            .willReturn(Mono.error(response))

        afmCalculatorClient
            .getPspDetails(PSP_ID, "AFM")
            .test()
            .expectError(RestApiException::class.java)
            .verify()
    }

    @ParameterizedTest
    @MethodSource("errorResponses")
    fun `Should return error when afm request fails directly throws an exception`(
        response: WebClientResponseException
    ) {
        given { afmCalculatorApi.getFeesMulti(any(), any(), any()) }.willThrow(response)

        afmCalculatorClient
            .getPspDetails(PSP_ID, "AFM")
            .test()
            .expectError(RestApiException::class.java)
            .verify()
    }

    companion object {
        private const val PSP_ID = "pspId"
        private const val PSP_BUSINESS_NAME = "pspBusinessName"
        private fun generateBundle() =
            BundleOption()
                .addBundleOptionsItem(Transfer().idPsp(PSP_ID).pspBusinessName(PSP_BUSINESS_NAME))

        @JvmStatic
        fun errorResponses(): Stream<Arguments> =
            Arrays.stream(org.springframework.http.HttpStatus.values())
                .filter { it.isError }
                .map { WebClientResponseException(it.value(), it.reasonPhrase, null, null, null) }
                .map { Arguments.of(it) }
    }
}

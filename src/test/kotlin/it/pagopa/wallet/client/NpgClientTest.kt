package it.pagopa.wallet.client

import it.pagopa.generated.npg.api.DefaultApi
import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.exception.NpgClientException
import java.nio.charset.StandardCharsets
import java.util.*
import kotlinx.coroutines.reactor.mono
import org.junit.jupiter.api.Test
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class NpgClientTest {

    private val defaultApi: DefaultApi = mock()

    private val npgClient = NpgClient(defaultApi)

    private val correlationId = UUID.randomUUID()

    @Test
    fun `Should communicate with NPG successfully`() {
        val hppRequest = WalletTestUtils.hppRequest()
        val hppResponse = WalletTestUtils.hppResponse()
        // prerequisite
        given(defaultApi.startPayment(correlationId, hppRequest)).willReturn(mono { hppResponse })
        // test and assertions
        StepVerifier.create(npgClient.orderHpp(correlationId, hppRequest))
            .expectNext(hppResponse)
            .verifyComplete()
    }

    @Test
    fun `Should map NPG error response to NpgClientException with BAD_GATEWAY error for exception during communication`() {
        val hppRequest = WalletTestUtils.hppRequest()
        // prerequisite
        given(defaultApi.startPayment(correlationId, hppRequest))
            .willThrow(
                WebClientResponseException.create(
                    500,
                    "statusText",
                    HttpHeaders.EMPTY,
                    ByteArray(0),
                    StandardCharsets.UTF_8
                )
            )
        // test and assertions
        StepVerifier.create(npgClient.orderHpp(correlationId, hppRequest))
            .expectErrorMatches {
                it as NpgClientException
                it.toRestException().httpStatus == HttpStatus.BAD_GATEWAY
            }
            .verify()
    }

    @Test
    fun `Should map NPG error response to NpgClientException with BAD_REQUEST error for NPG 400 response code`() {
        val hppRequest = WalletTestUtils.hppRequest()

        // prerequisite
        given(defaultApi.startPayment(correlationId, hppRequest))
            .willReturn(
                Mono.error(
                    WebClientResponseException.create(
                        400,
                        "statusText",
                        HttpHeaders.EMPTY,
                        ByteArray(0),
                        StandardCharsets.UTF_8
                    )
                )
            )
        // test and assertions
        StepVerifier.create(npgClient.orderHpp(correlationId, hppRequest))
            .expectErrorMatches {
                it as NpgClientException
                it.toRestException().httpStatus == HttpStatus.BAD_REQUEST
            }
            .verify()
    }

    @Test
    fun `Should map NPG error response to NpgClientException with UNAUTHORIZED error for NPG 401 response code`() {
        val hppRequest = WalletTestUtils.hppRequest()

        // prerequisite
        given(defaultApi.startPayment(correlationId, hppRequest))
            .willReturn(
                Mono.error(
                    WebClientResponseException.create(
                        401,
                        "statusText",
                        HttpHeaders.EMPTY,
                        ByteArray(0),
                        StandardCharsets.UTF_8
                    )
                )
            )
        // test and assertions
        StepVerifier.create(npgClient.orderHpp(correlationId, hppRequest))
            .expectErrorMatches {
                it as NpgClientException
                it.toRestException().httpStatus == HttpStatus.INTERNAL_SERVER_ERROR
            }
            .verify()
    }

    @Test
    fun `Should map NPG error response to NpgClientException with BAD_GATEWAY for other error codes`() {
        val hppRequest = WalletTestUtils.hppRequest()

        // prerequisite
        given(defaultApi.startPayment(correlationId, hppRequest))
            .willReturn(
                Mono.error(
                    WebClientResponseException.create(
                        503,
                        "statusText",
                        HttpHeaders.EMPTY,
                        ByteArray(0),
                        StandardCharsets.UTF_8
                    )
                )
            )
        // test and assertions
        StepVerifier.create(npgClient.orderHpp(correlationId, hppRequest))
            .expectErrorMatches {
                it as NpgClientException
                it.toRestException().httpStatus == HttpStatus.BAD_GATEWAY &&
                    it.toRestException().description.contains("503")
            }
            .verify()
    }
}

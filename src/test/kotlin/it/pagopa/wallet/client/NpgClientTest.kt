package it.pagopa.wallet.client

import it.pagopa.generated.npg.api.PaymentServicesApi
import it.pagopa.generated.npg.model.CreateHostedOrderRequest
import it.pagopa.generated.npg.model.Field
import it.pagopa.generated.npg.model.Fields
import it.pagopa.generated.npg.model.Order
import it.pagopa.wallet.exception.NpgClientException
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.reactor.mono
import org.junit.jupiter.api.Test
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.test.StepVerifier

class NpgClientTest {
    private val paymentServicesApi: PaymentServicesApi = mock()
    private val npgClient = NpgClient(paymentServicesApi)

    private val correlationId = UUID.randomUUID()
    private val createHostedOrderRequest =
        CreateHostedOrderRequest()
            .version("2")
            .merchantUrl("https://test")
            .order(Order().orderId(UUID.randomUUID().toString()))

    @Test
    fun `Should create payment order build successfully`() {
        val fields = Fields()
        fields.fields.addAll(
            listOf(
                Field().id(UUID.randomUUID().toString()).src("https://test.it/h").propertyClass("holder").propertyClass("h"),
                Field().id(UUID.randomUUID().toString()).src("https://test.it/p").propertyClass("pan").propertyClass("p"),
                Field().id(UUID.randomUUID().toString()).src("https://test.it/c").propertyClass("cvv").propertyClass("c")
            )
        )

        // prerequisite
        given(paymentServicesApi.apiOrdersBuildPost(correlationId, createHostedOrderRequest))
            .willReturn(mono { fields })

        // test and assertions
        StepVerifier.create(npgClient.createNpgOrderBuild(correlationId, createHostedOrderRequest))
            .expectNext(fields)
            .verifyComplete()
    }

    @Test
    fun `Should map error response to NpgClientException with BAD_GATEWAY error for exception during communication`() {
        // prerequisite
        given(paymentServicesApi.apiOrdersBuildPost(correlationId, createHostedOrderRequest))
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
        StepVerifier.create(npgClient.createNpgOrderBuild(correlationId, createHostedOrderRequest))
            .expectErrorMatches {
                it as NpgClientException
                it.toRestException().httpStatus == HttpStatus.BAD_GATEWAY
            }
            .verify()
    }

    @Test
    fun `Should map error response to NpgClientException with INTERNAL_SERVER_ERROR error for 401 during communication`() {
        // prerequisite
        given(paymentServicesApi.apiOrdersBuildPost(correlationId, createHostedOrderRequest))
            .willThrow(
                WebClientResponseException.create(
                    401,
                    "statusText",
                    HttpHeaders.EMPTY,
                    ByteArray(0),
                    StandardCharsets.UTF_8
                )
            )

        // test and assertions
        StepVerifier.create(npgClient.createNpgOrderBuild(correlationId, createHostedOrderRequest))
            .expectErrorMatches {
                it as NpgClientException
                it.toRestException().httpStatus == HttpStatus.INTERNAL_SERVER_ERROR
            }
            .verify()
    }

    @Test
    fun `Should map error response to NpgClientException with BAD_GATEWAY error for 500 from ecommerce-payment-methods`() {
        // prerequisite
        given(paymentServicesApi.apiOrdersBuildPost(correlationId, createHostedOrderRequest))
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
        StepVerifier.create(npgClient.createNpgOrderBuild(correlationId, createHostedOrderRequest))
            .expectErrorMatches {
                it as NpgClientException
                it.toRestException().httpStatus == HttpStatus.BAD_GATEWAY
            }
            .verify()
    }

    @Test
    fun `Should map error response to NpgClientException with BAD_GATEWAY error for 404 from ecommerce-payment-methods`() {
        // prerequisite
        given(paymentServicesApi.apiOrdersBuildPost(correlationId, createHostedOrderRequest))
            .willThrow(
                WebClientResponseException.create(
                    404,
                    "statusText",
                    HttpHeaders.EMPTY,
                    ByteArray(0),
                    StandardCharsets.UTF_8
                )
            )

        // test and assertions
        StepVerifier.create(npgClient.createNpgOrderBuild(correlationId, createHostedOrderRequest))
            .expectErrorMatches {
                it as NpgClientException
                it.toRestException().httpStatus == HttpStatus.BAD_GATEWAY
            }
            .verify()
    }

    @Test
    fun `Should map error response to NpgClientException with INTERNAL_SERVER_ERROR error for 400 from ecommerce-payment-methods`() {
        // prerequisite
        given(paymentServicesApi.apiOrdersBuildPost(correlationId, createHostedOrderRequest))
            .willThrow(
                WebClientResponseException.create(
                    400,
                    "statusText",
                    HttpHeaders.EMPTY,
                    ByteArray(0),
                    StandardCharsets.UTF_8
                )
            )

        // test and assertions
        StepVerifier.create(npgClient.createNpgOrderBuild(correlationId, createHostedOrderRequest))
            .expectErrorMatches {
                it as NpgClientException
                it.toRestException().httpStatus == HttpStatus.INTERNAL_SERVER_ERROR
            }
            .verify()
    }
}

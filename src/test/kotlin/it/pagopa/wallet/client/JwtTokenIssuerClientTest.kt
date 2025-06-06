package it.pagopa.wallet.client

import it.pagopa.generated.jwtIssuer.api.JwtIssuerApi
import it.pagopa.generated.jwtIssuer.model.CreateTokenRequest
import it.pagopa.generated.jwtIssuer.model.CreateTokenResponse
import it.pagopa.wallet.exception.JWTTokenGenerationException
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.reactor.mono
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class JwtTokenIssuerClientTest {

    private val jwtIssuerApi: JwtIssuerApi = mock()
    private val jwtTokenIssuerClient = JwtTokenIssuerClient(jwtIssuerApi)

    @Test
    fun `Should create jwt token successfully`() {
        val createTokenResponse = CreateTokenResponse().token("test")

        // prerequisite
        given(jwtIssuerApi.createJwtToken(any())).willReturn(mono { createTokenResponse })

        // test and assertions
        StepVerifier.create(
                jwtTokenIssuerClient.createToken(
                    CreateTokenRequest()
                        .duration(100)
                        .audience("test")
                        .privateClaims(mapOf("claim1" to "value1"))
                )
            )
            .expectNext(createTokenResponse)
            .verifyComplete()
    }

    @Test
    fun `Should get exception when jwt client throws exception`() {
        // prerequisite
        val createTokenRequest =
            CreateTokenRequest()
                .duration(100)
                .audience("test")
                .privateClaims(mapOf("claim1" to "value1"))
        given(jwtIssuerApi.createJwtToken(createTokenRequest))
            .willReturn(
                Mono.error(
                    WebClientResponseException.create(
                        HttpStatus.BAD_GATEWAY.value(),
                        "Error while invoking jwtIssuer",
                        HttpHeaders.EMPTY,
                        ByteArray(0),
                        StandardCharsets.UTF_8
                    )
                )
            )

        // test and assertions
        StepVerifier.create(jwtTokenIssuerClient.createToken(createTokenRequest))
            .expectErrorMatches {
                it as JWTTokenGenerationException
                it.toRestException().httpStatus == HttpStatus.BAD_GATEWAY
            }
            .verify()
    }

    @Test
    fun `Should get exception when jwt client throws unexpected exception`() {
        // prerequisite
        val createTokenRequest =
            CreateTokenRequest()
                .duration(100)
                .audience("test")
                .privateClaims(mapOf("claim1" to "value1"))
        given(jwtIssuerApi.createJwtToken(createTokenRequest))
            .willReturn(Mono.error(RuntimeException()))

        // test and assertions
        StepVerifier.create(jwtTokenIssuerClient.createToken(createTokenRequest))
            .expectErrorMatches {
                it as JWTTokenGenerationException
                it.toRestException().httpStatus == HttpStatus.INTERNAL_SERVER_ERROR
                it.toRestException().description == "Unexpected error while invoking jwtIssuer"
            }
            .verify()
    }
}

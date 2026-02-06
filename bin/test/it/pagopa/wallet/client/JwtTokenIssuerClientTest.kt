package it.pagopa.wallet.client

import it.pagopa.generated.jwtIssuer.api.JwtIssuerApi
import it.pagopa.generated.jwtIssuer.model.CreateTokenRequest
import it.pagopa.generated.jwtIssuer.model.CreateTokenResponse
import it.pagopa.wallet.exception.JWTTokenGenerationException
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.reactor.mono
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@SpringBootTest
@TestPropertySource(locations = ["classpath:application.test.properties"])
class JwtTokenIssuerClientTest {

    @MockBean private lateinit var pdvTokenizerClient: PdvTokenizerClient

    private val jwtIssuerApi: JwtIssuerApi = mock()
    private val jwtTokenIssuerClient = JwtTokenIssuerClient(jwtIssuerApi)
    @Autowired lateinit var realJwtIssuerApi: JwtIssuerApi
    @Value("\${jwt-issuer.apiKey}") lateinit var apiKey: String

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
                        .privateClaims(mapOf("claim1" to "value1"))))
            .expectNext(createTokenResponse)
            .verifyComplete()
    }

    @Test
    fun `Should inject api key header when making request to jwt issuer service`() {
        val createTokenResponse = CreateTokenResponse().token("test")
        val mockWebServer = MockWebServer()
        mockWebServer.start(8080)

        val jwtTokenIssuerClient = JwtTokenIssuerClient(realJwtIssuerApi)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"token":"test"}""")
                .addHeader("Content-Type", "application/json"))

        // test and assertions
        StepVerifier.create(
                jwtTokenIssuerClient.createToken(
                    CreateTokenRequest()
                        .duration(100)
                        .audience("test")
                        .privateClaims(mapOf("claim1" to "value1"))))
            .expectNext(createTokenResponse)
            .verifyComplete()
        val request = mockWebServer.takeRequest()

        // Assert the x-api-key header
        val apiKeyHeader = request.getHeader("x-api-key")
        assertEquals(apiKey, apiKeyHeader)

        mockWebServer.shutdown()
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
                        StandardCharsets.UTF_8)))

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

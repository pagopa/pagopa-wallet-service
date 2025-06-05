package it.pagopa.wallet.client

import it.pagopa.generated.jwtIssuer.api.JwtIssuerApi
import it.pagopa.generated.jwtIssuer.model.CreateTokenRequest
import it.pagopa.generated.jwtIssuer.model.CreateTokenResponse
import kotlinx.coroutines.reactor.mono
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
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
}

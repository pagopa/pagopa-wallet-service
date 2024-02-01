package it.pagopa.wallet.util

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtBuilder
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import io.vavr.control.Either
import it.pagopa.wallet.exception.JWTTokenGenerationException
import java.time.Duration
import java.util.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito
import org.mockito.Mockito

internal class JwtTokenUtilsTests {
    private val jwtSecretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(STRONG_KEY))
    private val jwtTokenUtils: JwtTokenUtils =
        JwtTokenUtils(jwtSecretKey, TOKEN_VALIDITY_TIME_SECONDS)
    @Test
    fun shouldGenerateValidJwtTokenWithOrderIdAndTransactionId() {
        val transactionIdAsClaim = UUID.randomUUID().toString()
        val walletAsClaim = UUID.randomUUID().toString()

        val generatedToken: Either<JWTTokenGenerationException, String> =
            jwtTokenUtils.generateJwtTokenForNpgNotifications(transactionIdAsClaim, walletAsClaim)
        Assertions.assertTrue(generatedToken.isRight)
        Assertions.assertNotNull(generatedToken)
        val claims =
            Assertions.assertDoesNotThrow<Claims> {
                Jwts.parserBuilder()
                    .setSigningKey(jwtSecretKey)
                    .build()
                    .parseClaimsJws(generatedToken.get())
                    .body
            }
        Assertions.assertEquals(
            transactionIdAsClaim,
            claims[JwtTokenUtils.TRANSACTION_ID_CLAIM, String::class.java]
        )
        Assertions.assertNotNull(claims.id)
        Assertions.assertNotNull(claims.issuedAt)
        Assertions.assertNotNull(claims.expiration)
        Assertions.assertEquals(
            Duration.ofSeconds(TOKEN_VALIDITY_TIME_SECONDS.toLong()).toMillis(),
            claims.expiration.time - claims.issuedAt.time
        )
    }

    @Test
    fun shouldGenerateExceptionJWTTokenGenerationException() {
        val transactionIdAsClaim = UUID.randomUUID().toString()
        val walletAsClaim = UUID.randomUUID().toString()

        Mockito.mockStatic(Jwts::class.java).use { mockedJwts ->
            val jwtBuilder = Mockito.mock(JwtBuilder::class.java)
            mockedJwts.`when`<Any> { Jwts.builder() }.thenReturn(jwtBuilder)
            BDDMockito.given(jwtBuilder.setId(ArgumentMatchers.any())).willReturn(jwtBuilder)
            BDDMockito.given(jwtBuilder.setIssuedAt(ArgumentMatchers.any())).willReturn(jwtBuilder)
            BDDMockito.given(jwtBuilder.setExpiration(ArgumentMatchers.any()))
                .willReturn(jwtBuilder)
            BDDMockito.given(jwtBuilder.signWith(ArgumentMatchers.any())).willReturn(jwtBuilder)
            BDDMockito.given(jwtBuilder.claim(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .willReturn(jwtBuilder)
            Mockito.doThrow(JwtException("Exception")).`when`(jwtBuilder).compact()
            val generatedToken: Either<JWTTokenGenerationException, String> =
                jwtTokenUtils.generateJwtTokenForNpgNotifications(
                    transactionIdAsClaim,
                    walletAsClaim
                )
            Assertions.assertTrue(generatedToken.isLeft)
            Assertions.assertEquals(
                JWTTokenGenerationException::class.java,
                generatedToken.left.javaClass
            )
        }
    }

    companion object {
        private const val STRONG_KEY =
            "ODMzNUZBNTZENDg3NTYyREUyNDhGNDdCRUZDNzI3NDMzMzQwNTFEREZGQ0MyQzA5Mjc1RjY2NTQ1NDk5MDMxNzU5NDc0NUVFMTdDMDhGNzk4Q0Q3RENFMEJBODE1NURDREExNEY2Mzk4QzFEMTU0NTExNjUyMEExMzMwMTdDMDk"
        private const val TOKEN_VALIDITY_TIME_SECONDS = 900
    }
}

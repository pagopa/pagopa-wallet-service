package it.pagopa.wallet.services

import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.client.NpgClient
import it.pagopa.wallet.exception.BadGatewayException
import it.pagopa.wallet.exception.InternalServerErrorException
import it.pagopa.wallet.repositories.WalletRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@OptIn(ExperimentalCoroutinesApi::class)
class WalletServiceTest {
    private val walletRepository: WalletRepository = mock()

    private val npgClient: NpgClient = mock()

    private val walletService: WalletService = WalletService(walletRepository, npgClient)

    @Test
    fun `createWallet creates wallet successfully`() = runTest {
        /* preconditions */
        val expected = Pair(WalletTestUtils.VALID_WALLET, WalletTestUtils.GATEWAY_REDIRECT_URL)

        given(walletRepository.save(any())).willReturn(Mono.just(expected.first))
        given(npgClient.orderHpp(any(), any())).willReturn(Mono.just(WalletTestUtils.hppResponse()))

        /* test */
        StepVerifier.create(walletService.createWallet())
            .expectNextMatches { it == expected }
            .verifyComplete()
    }

    @Test
    fun `createWallet throws InternalServerErrorException if it can't save wallet`() = runTest {
        /* preconditions */
        given(walletRepository.save(any()))
            .willReturn(Mono.error(RuntimeException("Error saving wallet")))
        given(npgClient.orderHpp(any(), any())).willReturn(Mono.just(WalletTestUtils.hppResponse()))
        StepVerifier.create(walletService.createWallet())
            .expectError(InternalServerErrorException::class.java)
            .verify()
    }

    @Test
    fun `createWallet throws BadGatewayException if it can't contact NPG`() = runTest {
        /* preconditions */
        given(walletRepository.save(any())).willReturn(Mono.just(WalletTestUtils.VALID_WALLET))
        given(npgClient.orderHpp(any(), any()))
            .willReturn(Mono.error(RuntimeException("NPG Error")))

        StepVerifier.create(walletService.createWallet())
            .expectError(BadGatewayException::class.java)
            .verify()
    }

    @Test
    fun `createWallet throws BadGatewayException if it doesn't receive redirectUrl from NPG`() =
        runTest {
            /* preconditions */
            given(walletRepository.save(any())).willReturn(Mono.empty())
            given(npgClient.orderHpp(any(), any()))
                .willReturn(Mono.just(WalletTestUtils.hppResponse().apply { hostedPage = null }))

            StepVerifier.create(walletService.createWallet())
                .expectError(BadGatewayException::class.java)
                .verify()
        }

    @Test
    fun `createWallet throws BadGatewayException if it doesn't receive securityToken from NPG`() =
        runTest {
            /* preconditions */
            given(walletRepository.save(any())).willReturn(Mono.empty())
            given(npgClient.orderHpp(any(), any()))
                .willReturn(Mono.just(WalletTestUtils.hppResponse().apply { securityToken = null }))

            StepVerifier.create(walletService.createWallet())
                .expectError(BadGatewayException::class.java)
                .verify()
        }
}

package it.pagopa.wallet.services

import it.pagopa.generated.wallet.model.TypeDto
import it.pagopa.generated.wallet.model.WalletCardDetailsDto
import it.pagopa.generated.wallet.model.WalletInfoDto
import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.client.NpgClient
import it.pagopa.wallet.domain.details.CardDetails
import it.pagopa.wallet.exception.BadGatewayException
import it.pagopa.wallet.exception.InternalServerErrorException
import it.pagopa.wallet.exception.WalletNotFoundException
import it.pagopa.wallet.repositories.WalletRepository
import java.time.OffsetDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.reactor.mono
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
        val expected =
            Pair(
                WalletTestUtils.VALID_WALLET_WITH_CARD_DETAILS,
                WalletTestUtils.GATEWAY_REDIRECT_URL
            )

        given(walletRepository.save(any())).willReturn(Mono.just(expected.first))
        given(npgClient.orderHpp(any(), any())).willReturn(Mono.just(WalletTestUtils.hppResponse()))

        /* test */
        StepVerifier.create(
                walletService.createWallet(
                    WalletTestUtils.CREATE_WALLET_REQUEST,
                    WalletTestUtils.USER_ID
                )
            )
            .expectNextMatches { it == expected }
            .verifyComplete()
    }

    @Test
    fun `createWallet throws InternalServerErrorException if it can't save wallet`() = runTest {
        /* preconditions */
        given(walletRepository.save(any()))
            .willReturn(Mono.error(RuntimeException("Error saving wallet")))
        given(npgClient.orderHpp(any(), any())).willReturn(Mono.just(WalletTestUtils.hppResponse()))
        StepVerifier.create(
                walletService.createWallet(
                    WalletTestUtils.CREATE_WALLET_REQUEST,
                    WalletTestUtils.USER_ID
                )
            )
            .expectError(InternalServerErrorException::class.java)
            .verify()
    }

    @Test
    fun `createWallet throws BadGatewayException if it can't contact NPG`() = runTest {
        /* preconditions */
        given(walletRepository.save(any()))
            .willReturn(Mono.just(WalletTestUtils.VALID_WALLET_WITH_CARD_DETAILS))
        given(npgClient.orderHpp(any(), any()))
            .willReturn(Mono.error(RuntimeException("NPG Error")))

        StepVerifier.create(
                walletService.createWallet(
                    WalletTestUtils.CREATE_WALLET_REQUEST,
                    WalletTestUtils.USER_ID
                )
            )
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

            StepVerifier.create(
                    walletService.createWallet(
                        WalletTestUtils.CREATE_WALLET_REQUEST,
                        WalletTestUtils.USER_ID
                    )
                )
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

            StepVerifier.create(
                    walletService.createWallet(
                        WalletTestUtils.CREATE_WALLET_REQUEST,
                        WalletTestUtils.USER_ID
                    )
                )
                .expectError(BadGatewayException::class.java)
                .verify()
        }

    @Test
    fun `getWallet return wallet successfully`() = runTest {
        /* precondition */

        val wallet = WalletTestUtils.VALID_WALLET_WITH_CARD_DETAILS
        val walletId = wallet.id
        val expectedWalletInfo =
            WalletInfoDto()
                .walletId(wallet.id.value)
                .userId(wallet.userId)
                .status(wallet.status)
                .creationDate(OffsetDateTime.parse(wallet.creationDate))
                .updateDate(OffsetDateTime.parse(wallet.updateDate))
                .paymentInstrumentId(wallet.paymentInstrumentId?.value.toString())
                .services(wallet.services)
                .details(
                    WalletCardDetailsDto()
                        .type(TypeDto.CARDS.toString())
                        .bin((wallet.details as CardDetails).bin)
                        .maskedPan((wallet.details as CardDetails).maskedPan)
                        .expiryDate((wallet.details as CardDetails).expiryDate)
                        .contractNumber((wallet.details as CardDetails).contractNumber)
                        .brand((wallet.details as CardDetails).brand)
                        .holder((wallet.details as CardDetails).holderName)
                )
        given(walletRepository.findById(walletId.value.toString())).willReturn(mono { wallet })

        /* Test */
        StepVerifier.create(walletService.getWallet(walletId.value))
            .expectNext(expectedWalletInfo)
            .verifyComplete()
    }

    @Test
    fun `getWallet return wallet successfully without payment instrument details`() = runTest {
        /* precondition */

        val wallet = WalletTestUtils.VALID_WALLET_WITHOUT_INSTRUMENT_DETAILS
        val walletId = wallet.id
        val expectedWalletInfo =
            WalletInfoDto()
                .walletId(wallet.id.value)
                .userId(wallet.userId)
                .status(wallet.status)
                .creationDate(OffsetDateTime.parse(wallet.creationDate))
                .updateDate(OffsetDateTime.parse(wallet.updateDate))
                .paymentInstrumentId(wallet.paymentInstrumentId?.value.toString())
                .services(wallet.services)

        given(walletRepository.findById(walletId.value.toString())).willReturn(mono { wallet })

        /* Test */
        StepVerifier.create(walletService.getWallet(walletId.value))
            .expectNext(expectedWalletInfo)
            .verifyComplete()
    }

    @Test
    fun `getWallet throw WalletNotFoundException for missing wallet into DB`() = runTest {
        /* precondition */

        val wallet = WalletTestUtils.VALID_WALLET_WITH_CARD_DETAILS
        val walletId = wallet.id

        given(walletRepository.findById(walletId.value.toString())).willReturn(Mono.empty())

        /* Test */
        StepVerifier.create(walletService.getWallet(walletId.value))
            .expectError(WalletNotFoundException::class.java)
            .verify()
    }
}

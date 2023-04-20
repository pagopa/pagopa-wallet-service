package it.pagopa.wallet.controllers

import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.exception.BadGatewayException
import it.pagopa.wallet.services.WalletService
import java.util.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.given
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class WalletControllerTest {
    private val walletService: WalletService = mock()

    private val walletController: WalletController = WalletController(walletService)

    @Test
    fun `wallet is created successfully`() = runTest {
        /* preconditions */
        given(walletService.createWallet())
            .willReturn(Pair(WalletTestUtils.VALID_WALLET, WalletTestUtils.GATEWAY_REDIRECT_URL))

        /* test */
        val walletId = walletController.createWallet(Any()).body?.walletId

        /* assertions */
        assertEquals(WalletTestUtils.VALID_WALLET.id.value, walletId)
    }

    @Test
    fun `rethrows if service raises BadGatewayException`() = runTest {
        /* preconditions */
        given(walletService.createWallet()).willThrow(BadGatewayException(""))

        /* assertions */
        assertThrows<BadGatewayException> { walletController.createWallet(Any()).body?.walletId }
    }
}

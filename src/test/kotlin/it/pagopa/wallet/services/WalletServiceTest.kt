package it.pagopa.wallet.services

import it.pagopa.wallet.repositories.WalletRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class WalletServiceTest {
    private val walletRepository: WalletRepository = mock()

    private val walletService: WalletService = WalletService(walletRepository)
}

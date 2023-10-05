package it.pagopa.wallet.domain.wallet

import it.pagopa.generated.wallet.model.WalletCardDetailsDto
import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.domain.common.ServiceStatus
import it.pagopa.wallet.domain.details.CardDetails
import it.pagopa.wallet.domain.wallets.Wallet
import it.pagopa.wallet.domain.wallets.WalletService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.Instant

class WalletTest {

    @Test
    fun `can construct wallet with empty services and null details`() {
        assertDoesNotThrow {
            Wallet(
                    WalletTestUtils.WALLET_UUID,
                    WalletTestUtils.USER_ID,
                    WalletStatusDto.CREATED,
                    Instant.now(),
                    Instant.now(),
                    WalletTestUtils.PAYMENT_METHOD_ID,
                    WalletTestUtils.PAYMENT_INSTRUMENT_ID,
                    listOf(),
                    WalletTestUtils.CONTRACT_ID,
                    null
            )
        }
    }

    @Test
    fun `can construct wallet with services and null details`() {
        assertDoesNotThrow {
            Wallet(
                    WalletTestUtils.WALLET_UUID,
                    WalletTestUtils.USER_ID,
                    WalletStatusDto.CREATED,
                    Instant.now(),
                    Instant.now(),
                    WalletTestUtils.PAYMENT_METHOD_ID,
                    WalletTestUtils.PAYMENT_INSTRUMENT_ID,
                    listOf(WalletService(WalletTestUtils.SERVICE_ID, WalletTestUtils.SERVICE_NAME, ServiceStatus.DISABLED, Instant.now())),
                    WalletTestUtils.CONTRACT_ID,
                    null
            )
        }
    }

    @Test
    fun `can construct wallet with services and card details`() {

        val bin = "424242"
        val maskedPan = "424242******5555"
        val expiryDate = "203012"
        val brand = WalletCardDetailsDto.BrandEnum.MASTERCARD
        val holderName = "holderName"
        val validType = "CARDS"

        assertDoesNotThrow {
            Wallet(
                    WalletTestUtils.WALLET_UUID,
                    WalletTestUtils.USER_ID,
                    WalletStatusDto.CREATED,
                    Instant.now(),
                    Instant.now(),
                    WalletTestUtils.PAYMENT_METHOD_ID,
                    WalletTestUtils.PAYMENT_INSTRUMENT_ID,
                    listOf(WalletService(WalletTestUtils.SERVICE_ID, WalletTestUtils.SERVICE_NAME, ServiceStatus.DISABLED, Instant.now())),
                    WalletTestUtils.CONTRACT_ID,
                    CardDetails(validType, bin, maskedPan, expiryDate, brand, holderName)
            )
        }
    }
}

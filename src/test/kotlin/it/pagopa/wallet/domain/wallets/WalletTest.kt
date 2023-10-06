package it.pagopa.wallet.domain.wallets

import it.pagopa.generated.wallet.model.WalletStatusDto
import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.domain.common.ServiceStatus
import it.pagopa.wallet.domain.details.CardDetails
import java.time.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

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
                listOf(
                    WalletService(
                        WalletTestUtils.SERVICE_ID,
                        WalletTestUtils.SERVICE_NAME,
                        ServiceStatus.DISABLED,
                        Instant.now()
                    )
                ),
                WalletTestUtils.CONTRACT_ID,
                null
            )
        }
    }

    @Test
    fun `can construct wallet with services and card details`() {

        assertDoesNotThrow {
            Wallet(
                WalletTestUtils.WALLET_UUID,
                WalletTestUtils.USER_ID,
                WalletStatusDto.CREATED,
                Instant.now(),
                Instant.now(),
                WalletTestUtils.PAYMENT_METHOD_ID,
                WalletTestUtils.PAYMENT_INSTRUMENT_ID,
                listOf(
                    WalletService(
                        WalletTestUtils.SERVICE_ID,
                        WalletTestUtils.SERVICE_NAME,
                        ServiceStatus.DISABLED,
                        Instant.now()
                    )
                ),
                WalletTestUtils.CONTRACT_ID,
                CardDetails(
                    WalletTestUtils.BIN,
                    WalletTestUtils.MASKED_APN,
                    WalletTestUtils.EXP_DATE,
                    WalletTestUtils.BRAND,
                    WalletTestUtils.HOLDER_NAME
                )
            )
        }
    }
}

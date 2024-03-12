package it.pagopa.wallet.util

import it.pagopa.generated.wallet.model.WalletCardDetailsDto
import it.pagopa.wallet.WalletTestUtils
import it.pagopa.wallet.domain.wallets.Wallet
import it.pagopa.wallet.domain.wallets.details.CardDetails
import it.pagopa.wallet.domain.wallets.details.WalletDetailsType
import java.net.URI
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class LogoUtilsTest {

    companion object {
        val logoMap =
            (WalletCardDetailsDto.BrandEnum.values().map { it.toString() } +
                    WalletDetailsType.values()
                        .filter { it != WalletDetailsType.CARDS }
                        .map { it.toString() } +
                    LogoUtils.UNKNOWN_LOGO_KEY)
                .associateBy({ it }, { URI.create("http://logoUrl/$it") })

        @JvmStatic
        fun `card wallet with all brands method source`() =
            WalletCardDetailsDto.BrandEnum.values().map {
                WalletTestUtils.walletDocumentStatusValidatedCard(it).toDomain()
            }
    }

    private val logoUtils = LogoUtils(logoMap)

    @ParameterizedTest
    @MethodSource("card wallet with all brands method source")
    fun `should retrieve logo for input card wallet successfully`(wallet: Wallet) {
        val logoURI = logoUtils.getLogo(wallet)
        assertTrue(logoURI.toString().endsWith("/${(wallet.details as CardDetails).brand}"))
    }

    @Test
    fun `should retrieve logo for input apm wallet successfully`() {
        val wallet = WalletTestUtils.walletDocumentStatusValidatedAPM("test@test.it").toDomain()
        val logoURI = logoUtils.getLogo(wallet)
        assertTrue(logoURI.toString().endsWith("/PAYPAL"))
    }

    @Test
    fun `should retrieve logo for input for a wallet without details`() {
        val wallet = WalletTestUtils.walletDocumentNullDetails().toDomain()
        val logoURI = logoUtils.getLogo(wallet)
        assertTrue(logoURI.toString().endsWith("/UNKNOWN"))
    }
}

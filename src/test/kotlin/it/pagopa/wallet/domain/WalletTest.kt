package it.pagopa.wallet.domain

import it.pagopa.generated.wallet.model.ServiceDto
import it.pagopa.generated.wallet.model.TypeDto
import it.pagopa.generated.wallet.model.WalletStatusDto
import java.time.OffsetDateTime
import java.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WalletTest {
    @Test
    fun `can construct wallet from UUID`() {
        val walletId = WalletId(UUID.randomUUID())
        val securityToken = UUID.randomUUID().toString()
        val now = OffsetDateTime.now().toString()
        val userId = UUID.randomUUID().toString()
        val contractNumber = UUID.randomUUID().toString().replace("-", "")

        val wallet =
            Wallet(
                walletId,
                userId,
                WalletStatusDto.INITIALIZED,
                now,
                now,
                TypeDto.CARDS,
                null,
                securityToken,
                listOf(ServiceDto.PAGOPA),
                contractNumber,
                null
            )

        assertEquals(walletId, wallet.id)
    }

    @Test
    fun `wallet with empty payment instrument list is invalid`() {
        val userId = UUID.randomUUID().toString()
        val services = listOf<ServiceDto>()
        val walletId = WalletId(UUID.randomUUID())
        val securityToken = UUID.randomUUID().toString()
        val now = OffsetDateTime.now().toString()
        val contractNumber = UUID.randomUUID().toString().replace("-", "")

        assertThrows<IllegalArgumentException> {
            Wallet(
                walletId,
                userId,
                WalletStatusDto.INITIALIZED,
                now,
                now,
                TypeDto.CARDS,
                null,
                securityToken,
                services,
                contractNumber,
                null
            )
        }
    }
}

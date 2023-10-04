package it.pagopa.wallet.domain

class WalletTest {
    /* @Test
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
    }*/
}

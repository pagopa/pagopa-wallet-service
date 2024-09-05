package it.pagopa.wallet.domain.wallets.details

import it.pagopa.wallet.audit.AuditWalletDetails

data class PayPalDetails(
    val maskedEmail: MaskedEmail?,
    val pspId: String,
    val pspBusinessName: String
) : WalletDetails<PayPalDetails> {

    override val type: WalletDetailsType
        get() = WalletDetailsType.PAYPAL

    override fun toDocument() =
        it.pagopa.wallet.documents.wallets.details.PayPalDetails(
            maskedEmail?.value,
            pspId,
            pspBusinessName
        )

    override fun toAudit(): AuditWalletDetails {
        return AuditWalletDetails(type = this.type.name, cardBrand = null, pspId = this.pspId)
    }
}

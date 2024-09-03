package it.pagopa.wallet.audit

data class AuditWalletDetails(
    val type: String,
    val expiryDate: String?,
    val cardBrand: String?,
    val pspId: String?
) {}

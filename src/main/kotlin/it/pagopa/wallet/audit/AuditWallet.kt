package it.pagopa.wallet.audit

/** Data class that contains wallet details for a log event */
data class AuditWallet(
    val paymentMethodId: String,
    val creationDate: String,
    val updateDate: String,
    var applications: List<AuditWalletApplication>,
    var details: AuditWalletDetails?,
    var status: String,
    var validationOperationId: String?,
    var validationOperationResult: String?,
    var validationOperationTimestamp: String?,
    var validationErrorCode: String?
)

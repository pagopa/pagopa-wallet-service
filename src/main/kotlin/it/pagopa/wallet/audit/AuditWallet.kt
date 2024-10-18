package it.pagopa.wallet.audit

/** Data class base */
sealed class AuditWallet()

/** Data class that contains wallet details for a log event */
data class AuditWalletCreated(val orderId: String) : AuditWallet()

/** Data class that contains wallet details for a log event */
data class AuditWalletCompleted(
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
) : AuditWallet()

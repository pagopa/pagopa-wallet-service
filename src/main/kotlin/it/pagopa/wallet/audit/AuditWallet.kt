package it.pagopa.wallet.audit

data class AuditWallet(
    val paymentMethodId: String,
    val userId: String,
    val creationDate: String,
    val updateDate: String,
    var applications: List<AuditWalletApplication>,
    var details: AuditWalletDetails?,
    var status: String,
    var validationOperationId: String?,
    var validationOperationResult: String?,
    var validationOperationTimestamp: String?,
    var validationErrorCode: String?,
    val onboardingChannel: String
) {}

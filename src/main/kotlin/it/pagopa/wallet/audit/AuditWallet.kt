package it.pagopa.wallet.audit

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/** Data class base */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = AuditWalletCreated::class, name = "AuditWalletCreated"),
    JsonSubTypes.Type(value = AuditWalletCompleted::class, name = "AuditWalletCompleted"),
)
sealed class AuditWallet

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

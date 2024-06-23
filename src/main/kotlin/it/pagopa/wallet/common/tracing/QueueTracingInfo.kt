package it.pagopa.wallet.common.tracing

data class QueueTracingInfo(
    val traceparent: String?,
    val tracestate: String?,
    val baggage: String?
) {
    companion object {
        fun empty() = QueueTracingInfo("", "", "")
    }
}

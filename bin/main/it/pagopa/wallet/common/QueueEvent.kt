package it.pagopa.wallet.common

import it.pagopa.wallet.audit.WalletQueueEvent
import it.pagopa.wallet.common.tracing.QueueTracingInfo

data class QueueEvent<T : WalletQueueEvent>(val data: T, val tracingInfo: QueueTracingInfo)

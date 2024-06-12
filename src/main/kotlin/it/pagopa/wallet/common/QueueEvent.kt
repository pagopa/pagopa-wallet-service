package it.pagopa.wallet.common

import it.pagopa.wallet.audit.WalletEvent
import it.pagopa.wallet.util.QueueTracingInfo

data class QueueEvent<T : WalletEvent>(val data: T, val tracingInfo: QueueTracingInfo)

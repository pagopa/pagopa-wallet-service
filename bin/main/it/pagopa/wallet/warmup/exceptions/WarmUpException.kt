package it.pagopa.wallet.warmup.exceptions

class WarmUpException(controllerName: String, functionName: String) :
    RuntimeException("Exception performing warm-up function $controllerName.$functionName") {}

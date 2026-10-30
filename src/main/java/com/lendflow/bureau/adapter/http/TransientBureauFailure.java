package com.lendflow.bureau.adapter.http;

/** A failure worth retrying: timeout, connection error or 5xx. A 4xx means our request is wrong and is not. */
class TransientBureauFailure extends RuntimeException {

    TransientBureauFailure(String message) {
        super(message);
    }
}

package com.yumi.bootloom.abank.domain;

import java.lang.ScopedValue;
import java.util.UUID;

public final class RequestContext {
    private static final ScopedValue<UUID> REQUEST_ID = ScopedValue.newInstance();

    private RequestContext() {
        throw new AssertionError("No instances for you!");
    }

    public static Request withRequestId(UUID requestId) {
        return new Request(ScopedValue.where(REQUEST_ID, requestId));
    }

    public static UUID getRequestId() {
        return REQUEST_ID.orElseThrow(() -> new Model.ABankException("No request ID available"));
    }

    public static class Request {
        private final ScopedValue.Carrier carrier;

        private Request(ScopedValue.Carrier carrier) {
            this.carrier = carrier;
        }

        public <T,X extends Throwable> T call(ScopedValue.CallableOp<T, X> op) throws X {
            return this.carrier.call(op);
        }
    }
}

package com.yumi.basic.contract;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

public class NullNotNullTest {

    @NullMarked
    public static class MethodTester {

        public void logMsgLength(String message) {
            System.out.println(message.length());
        }

        public void logMsgUpperCase(String message) {
            System.out.println(message.toUpperCase());
        }
    }

    public static void testNullable(@Nullable String message) {
        if (message == null) {
            message = "default";
        }
        System.out.println(message.length());
    }

    public static void testNotNull(@NonNull String message) {
        System.out.println(message.length());
    }

    public static @NonNull String getMessageNonNull() {
        return "hello";
    }

    public static @Nullable String getMessageNullable() {
        return System.currentTimeMillis() % 2 == 0 ? "hello" : null;
    }

    static void main() {
        var tester = new MethodTester();
        tester.logMsgLength(null);
        tester.logMsgUpperCase("hello");
        testNullable(null);
        testNotNull(null);
        var r1 = getMessageNonNull();
        System.out.println(r1.length());
        var r2 = getMessageNullable();
        System.out.println(r2.length());
    }
}

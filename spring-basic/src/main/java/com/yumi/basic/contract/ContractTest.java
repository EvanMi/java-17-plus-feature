package com.yumi.basic.contract;


import org.jspecify.annotations.Nullable;
import org.springframework.lang.Contract;

import java.util.Objects;

public class ContractTest {

    public static class Lily {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Contract("null -> fail")
    public static void verify(Lily lily) {
        if (Objects.isNull(lily)) {
            throw new IllegalArgumentException("lily is null");
        }
    }

    public static void test(@Nullable Lily lily) {
        verify(lily);
        var x = lily.getName();
    }

    static void main() {
        test(null);
    }
}

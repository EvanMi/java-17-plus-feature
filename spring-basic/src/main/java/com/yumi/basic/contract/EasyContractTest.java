package com.yumi.basic.contract;

import java.util.Objects;

public class EasyContractTest {

    public static class Lily {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static void verify(Lily lily) {
        if (null == lily) {
            throw new IllegalArgumentException("lily is null");
        }
    }

    public static void test(Lily lily) {
        verify(lily);
        var x = lily.getName();
    }

    void main() {
        System.out.println(Long.MAX_VALUE);
        System.out.println(String.valueOf(Long.MAX_VALUE).length());
        //test(null);
    }
}

package com.yumi.baseusage.abs;

public abstract class AnimalClass {

    public abstract String getName();

    public static class Abc {
        private final Object res;

        public Abc(Object res) {
            this.res = res;
        }

        @SuppressWarnings("unchecked")
        public <T> T getRes() {
            return (T) res;
        }
    }
}

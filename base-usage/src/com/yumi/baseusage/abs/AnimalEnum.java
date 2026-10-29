package com.yumi.baseusage.abs;

public enum AnimalEnum {
    DOG {
        @Override
        public String getName() {
            return "Dog";
        }
    }, CAT {
        @Override
        public String getName() {
            return "Cat";
        }
    };

    public abstract String getName();
}

package com.yumi.baseusage.phantom_reference;

public abstract class TypeEnum<T> {
    protected String code;
    protected Class<T> clazz;

    public TypeEnum(String code, Class<T> clazz) {
        this.code = code;
        this.clazz = clazz;
    }

    public String getCode() {
        return code;
    }

    public Class<T> getClazz() {
        return clazz;
    }

    public static abstract class StringTypeEnum extends TypeEnum<String> {
        public StringTypeEnum(String code) {
            super(code, String.class);
        }
    }

    public static abstract class IntegerTypeEnum extends TypeEnum<Integer> {
        public IntegerTypeEnum(String code) {
            super(code, Integer.class);
        }
    }

    public static <T> T getRest(TypeEnum<T> typeEnum) {
        if (typeEnum instanceof StringTypeEnum) {
            if ("SUCCESS".equals(typeEnum.getCode())) {
                return (T) "SUCCESS";
            } else {
                return (T) "FAIL";
            }
        }
        if (typeEnum instanceof IntegerTypeEnum) {
            if ("SUCCESS".equals(typeEnum.getCode())) {
                return (T) Integer.valueOf(200);
            } else {
                return (T) Integer.valueOf(500);
            }
        }
        return null;
    }


    static void main() {
        String rest = getRest(new StringTypeEnum("aa") {
        });
        System.out.println(rest);
        Integer rest1 = getRest(new IntegerTypeEnum("bb") {
        });
        System.out.println(rest1);

    }
}
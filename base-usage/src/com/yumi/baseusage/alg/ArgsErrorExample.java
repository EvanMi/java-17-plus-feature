package com.yumi.baseusage.alg;

import com.alibaba.fastjson2.JSON;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArgsErrorExample {

    static void main(String[] args) {
        List<Object> result = new ArrayList<>();

        String[] strArr = new String[]{"a", "b", "c"};
        Collections.addAll(result, strArr);
        printJson(result);

        Integer[] INTS = new Integer[]{1, 2};
        Collections.addAll(result, INTS);
        printJson(result);

        Collections.addAll(result, 3, 4);
        printJson(result);

        int[] ints = new int[]{5, 6};
        Collections.addAll(result, ints);
        printJson(result);

    }

    static void printJson(Object obj) {
        IO.println(JSON.toJSONString(obj));
        IO.println("----------------");
        IO.readln("是否继续执行?");
    }
}

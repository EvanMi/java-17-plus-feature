package com.yumi.uuid.v7.demo;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;

import java.util.UUID;


/// | 48 bit unix_ts_ms |4bit ver|12bit rand_a|2bit var|62bit rand_b|
public class V7Test {
    public static void main(String[] args) {
        testV7();
    }


    static void testV7() {
        System.out.println("axx_encrypt".substring(0, "axx_encrypt".lastIndexOf("_encrypt")));

        var binaryCurrentTime = Long.toBinaryString(System.currentTimeMillis());
        System.out.println(binaryCurrentTime);
        System.out.println(binaryCurrentTime.length());
        // 公元 10889 年，应该不用担心
        System.out.println("0".repeat(48 - binaryCurrentTime.length()) + binaryCurrentTime);

        //同一毫秒内生成~有累加特性
        var timeBasedEpochGenerator = Generators.timeBasedEpochGenerator();
        System.out.println(timeBasedEpochGenerator.generate());
        System.out.println(timeBasedEpochGenerator.generate());
        System.out.println(timeBasedEpochGenerator.generate());
        System.out.println(timeBasedEpochGenerator.generate());
        System.out.println(timeBasedEpochGenerator.generate());
        System.out.println(timeBasedEpochGenerator.generate());
        //74 bit 熵。跨进程碰撞概率是 2⁻⁷⁴ 量级
        System.out.println("=================================");
        //每一次都随机
        var randomGenerator = Generators.timeBasedEpochRandomGenerator();
        System.out.println(randomGenerator.generate());
        System.out.println(randomGenerator.generate());
        System.out.println(randomGenerator.generate());
        System.out.println(randomGenerator.generate());
        System.out.println(randomGenerator.generate());
        System.out.println(randomGenerator.generate());
    }
}

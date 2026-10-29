package com.yumi.baseusage.alg;

import java.util.ArrayList;
import java.util.List;

public class KMPDemo {

    /**
     * 构造next数组（前缀函数）
     * next[i]：pattern[0..i]子串的最长相等前后缀长度
     */
    public static int[] getNext(String pattern) {
        int m = pattern.length();
        int[] next = new int[m];
        int len = 0; // 当前最长相等前后缀长度，同时是下一个前缀对比下标
        int i = 1;   // 后缀游标，从下标1开始，next[0]固定=0

        while (i < m) {
            if (pattern.charAt(i) == pattern.charAt(len)) {
                // 字符匹配，前后缀延长一位
                len++;
                next[i] = len;
                i++;
            } else {
                // 不匹配，回退len，i原地不动！
                if (len != 0) {
                    len = next[len - 1];
                } else {
                    // len退到0，没有相等前后缀
                    next[i] = 0;
                    i++;
                }
            }
        }
        return next;
    }

    /**
     * KMP匹配主逻辑
     * @param text 文本串
     * @param pattern 模式串
     * @return 所有匹配成功的起始下标
     */
    public static List<Integer> kmpSearch(String text, String pattern) {
        List<Integer> result = new ArrayList<>();
        int n = text.length();
        int m = pattern.length();
        if (m == 0 || n < m) {
            return result;
        }
        int[] next = getNext(pattern);

        // i：文本串指针；j：模式串指针
        int i = 0;
        int j = 0;
        while (i < n) {
            if (text.charAt(i) == pattern.charAt(j)) {
                i++;
                j++;
            }
            if (j == m) {
                // 找到一个匹配，记录起始位置
                result.add(i - j);
                // 继续找下一个，模式串回退
                j = next[j - 1];
            } else if (i < n && text.charAt(i) != pattern.charAt(j)) {
                // 字符不相等
                if (j != 0) {
                    j = next[j - 1]; // 核心：模式串回退，文本i不动
                } else {
                    i++; // j已经是0，没得退，文本指针往前走
                }
            }
        }
        return result;
    }

    public static void main(String[] args) {
        String text = "ababcabababc";
        String pattern = "abababc";
        int[] next = getNext(pattern);
        System.out.println("pattern:" + pattern);
        System.out.println("next数组：");
        for (int num : next) {
            System.out.print(num + " ");
        }
        System.out.println();

        List<Integer> posList = kmpSearch(text, pattern);
        System.out.println("匹配到的起始下标：" + posList);
    }
}


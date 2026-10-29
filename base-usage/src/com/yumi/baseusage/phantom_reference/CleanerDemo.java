package com.yumi.baseusage.phantom_reference;

import java.lang.ref.Cleaner;

public class CleanerDemo {
    // 1. 创建全局 Cleaner（内部自带守护线程、ReferenceQueue）
    private static final Cleaner cleaner = Cleaner.create();

    // 清理动作：单独静态内部类，不持有外部对象引用，避免内存泄漏
    static class CleanTask implements Runnable {
        @Override
        public void run() {
            System.out.println("释放堆外内存，free native buffer");
        }
    }

    static void main(String[] args) throws InterruptedException {
        Object directMem = new Object();

        // 2. 注册：目标对象 + 清理任务；返回 Cleanable，可手动 clean
        cleaner.register(directMem, new CleanTask());

        // 切断强引用
        directMem = null;

        // 建议 GC（仅测试用）
        System.gc();

        Thread.sleep(3000);
        System.out.println("main 结束");
    }
}

package com.yumi.baseusage.phantom_reference;

import java.lang.ref.Cleaner;

public class CleanerFullDemo {
    private static final Cleaner CLEANER = Cleaner.create();

    static class Resource implements AutoCloseable {
        // 注册返回Cleanable，保存这个对象，可手动执行clean
        private final Cleaner.Cleanable cleanable;

        public Resource() {
            // 第一个参数：要监控的对象this；第二个：清理任务Runnable
            cleanable = CLEANER.register(this, new Runnable() {
                @Override
                public void run() {
                    // 注意：run内部**不要捕获this（Resource实例）！！！会阻止GC！**
                    System.out.println("【Cleaner】执行资源释放（GC触发）");
                }
            });
        }

        @Override
        public void close() {
            // 手动释放；如果已经GC自动释放，调用clean是幂等，不会重复执行
            cleanable.clean();
            System.out.println("【Cleaner】手动调用close");
        }
    }

static void main(String[] args) throws InterruptedException {
    Resource resource = new Resource();
    // 手动关闭
    resource.close();

    // 切断强引用，交由Cleaner自动回收
    resource = null;

    System.gc();
    Thread.sleep(500);
    System.out.println("main结束");
}
}

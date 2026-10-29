package com.yumi.baseusage.phantom_reference;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.ConcurrentHashMap;

public class CleanerSimulate {
    static ReferenceQueue<Object> queue = new ReferenceQueue<>();

    static class ResourceCleaner extends PhantomReference<Object> {
        private final Runnable cleanTask;

        public ResourceCleaner(Object referent, Runnable cleanTask) {
            super(referent, queue);
            this.cleanTask = cleanTask;
        }

        public void clean() {
            cleanTask.run();
        }
    }


    static void main(String[] args) throws InterruptedException {
        Object directMem = new Object();
        // 绑定回收回调：释放堆外内存
       var rr = new ResourceCleaner(directMem, () -> System.out.println("释放堆外内存，free native buffer"));

        directMem = null;
        System.gc();
        Thread.sleep(200);

        // 清理线程
        var thread = new Thread(() -> {
            for (; ; ) {
                try {
                    ResourceCleaner cleaner = (ResourceCleaner) queue.remove();
                    cleaner.clean();
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "cleaner-thread");
        thread.setDaemon(true);
        thread.start();

        Thread.sleep(3000);
    }
}

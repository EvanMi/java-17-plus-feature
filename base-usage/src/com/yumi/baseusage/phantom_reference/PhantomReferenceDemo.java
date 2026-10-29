package com.yumi.baseusage.phantom_reference;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.TimeUnit;

public class PhantomReferenceDemo {
    // 自定义需要被虚引用管理的对象
    static class Resource {
        private final String name;

        public Resource(String name) {
            this.name = name;
        }

        // 模拟释放本地资源（文件、Socket、堆外内存等）
        public void release() {
            System.out.println("资源[" + name + "] 已手动释放");
        }
    }


static void main() throws InterruptedException {
    // 1. 创建引用队列，GC回收对象后，虚引用实例会入队
    ReferenceQueue<Resource> queue = new ReferenceQueue<>();

    // 2. 创建资源对象
    Resource resource = new Resource("磁盘文件资源");

    // 3. 构建虚引用，绑定资源 + 引用队列
    PhantomReference<Resource> phantomRef = new PhantomReference<>(resource, queue);

    // 虚引用 get() 永远返回 null，无法拿到原对象
    System.out.println("phantomRef.get() = " + phantomRef.get());
    assert phantomRef.get() == null;

    // 4. 切断强引用，让对象具备被GC的条件
    resource = null;

    // 5. 主动触发GC
    System.gc();
    TimeUnit.MILLISECONDS.sleep(200);

    // 6. 轮询引用队列，检测对象是否被回收
    Reference<?> ref;
    while ((ref = queue.poll()) != null) {
        if (ref == phantomRef) {
            System.out.println("检测到资源被GC回收，开始执行后置清理逻辑");
            // 此处不能拿到原Resource对象，只能做善后清理工作
            Object o = ref.get();
            assert o == null;
            // 典型场景：释放Native内存、关闭句柄、删除临时文件等
            System.out.println("完成资源收尾清理工作");
        }
    }

    TimeUnit.SECONDS.sleep(2);
}
}

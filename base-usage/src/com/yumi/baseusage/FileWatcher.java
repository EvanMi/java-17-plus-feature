package com.yumi.baseusage;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;

public class FileWatcher {

    void main() throws Exception {
        // 要监听的目录
        var watchDir = Paths.get("");
        // 创建 WatchService 监听服务
        var watchService = FileSystems.getDefault().newWatchService();
        // 注册监听事件：创建、修改、删除
        watchDir.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
        IO.println("开始监听目录：" + watchDir);
        IO.println("请对目录内文件进行操作（创建/修改/删除）...");

        // 4. 循环监听事件（无限监听）
        while (true) {
            // 阻塞等待事件发生
            var key = watchService.take();

            // 遍历事件
            for (var event : key.pollEvents()) {
                var kind = event.kind();
                // 处理事件丢失异常
                if (kind.equals(StandardWatchEventKinds.OVERFLOW)) {
                    IO.println("事件溢出！");
                    continue;
                }

                // 获取触发事件的文件名
                @SuppressWarnings("unchecked")
                WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                Path fileName = pathEvent.context();

                // 打印事件
                IO.println("【" + kind.name() + "】 -> " + fileName);
            }

            // 必须重置 key，否则无法继续监听
            boolean valid = key.reset();
            if (!valid) {
                IO.println("监听已失效！");
                break;
            }
        }
    }
}

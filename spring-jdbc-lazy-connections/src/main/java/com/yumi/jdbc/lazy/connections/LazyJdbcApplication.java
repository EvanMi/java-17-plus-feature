package com.yumi.jdbc.lazy.connections;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LazyJdbcApplication {

    /**
     * 应用程序入口方法
     *
     * @param args 命令行参数
     */
    static void main(String[] args) {
        SpringApplication.run(LazyJdbcApplication.class, args);
    }

}

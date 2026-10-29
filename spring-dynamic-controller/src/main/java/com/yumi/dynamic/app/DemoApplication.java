package com.yumi.dynamic.app;

import com.yumi.dynamic.app.dynamic.DynamicControllerBeanRegistrar;
import com.yumi.dynamic.app.dynamic.DynamicControllerRegistrar;
import com.yumi.dynamic.app.dynamic.EnableDynamicController;
import com.yumi.dynamic.controller.HelloController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableDynamicController(enableOrder = true)
//@Import(DynamicControllerRegistrar.class)
//@Import(DynamicControllerBeanRegistrar.class)
@Import(HelloController.class)
public class DemoApplication {
    void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

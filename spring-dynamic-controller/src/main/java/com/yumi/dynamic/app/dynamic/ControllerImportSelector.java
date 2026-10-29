package com.yumi.dynamic.app.dynamic;

import com.yumi.dynamic.controller.OrderController;
import com.yumi.dynamic.controller.UserController;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ControllerImportSelector implements ImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        List<String> classNames = new ArrayList<>();
        // 获取注解上的属性
        Map<String, Object> attr = importingClassMetadata.getAnnotationAttributes(EnableDynamicController.class.getName());
        if (Objects.nonNull(attr)) {
            boolean loadOrder = (boolean) attr.get("enableOrder");
            // 根据注解参数动态决定是否加载订单Controller
            if (loadOrder) {
                classNames.add(OrderController.class.getName());
            }
        }
        // 必加载用户Controller
        classNames.add(UserController.class.getName());
        return classNames.toArray(new String[0]);
    }
}

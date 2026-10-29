package com.yumi.dynamic.app.dynamic;

import com.yumi.dynamic.controller.HelloController;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.BeanRegistry;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

public class DynamicControllerBeanRegistrar implements BeanRegistrar, ImportAware {

    private AnnotationMetadata importingClassMetadata;

    // ImportAware回调：注入@Import所在类的元信息
    @Override
    public void setImportMetadata(@NonNull AnnotationMetadata importMetadata) {
        this.importingClassMetadata = importMetadata;
    }

    @Override
    public void register(BeanRegistry registry, @NonNull Environment env) {
        registry.registerBean("helloController", HelloController.class);
    }
}

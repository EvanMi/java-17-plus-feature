package com.yumi.dynamic.app.dynamic;

import com.yumi.dynamic.controller.HelloController;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

public class DynamicControllerRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(@NonNull AnnotationMetadata importingClassMetadata, @NonNull BeanDefinitionRegistry registry) {
        GenericBeanDefinition beanDef = new GenericBeanDefinition();
        beanDef.setBeanClassName(HelloController.class.getName());
        beanDef.setScope("singleton");
        registry.registerBeanDefinition("helloController", beanDef);
    }
}
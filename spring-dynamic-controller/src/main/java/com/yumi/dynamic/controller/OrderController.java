package com.yumi.dynamic.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {
    @GetMapping("/order/list")
    public String getOrder() {
        return "订单模块接口";
    }
}

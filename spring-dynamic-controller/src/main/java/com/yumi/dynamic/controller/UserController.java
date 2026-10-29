package com.yumi.dynamic.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {
    @GetMapping("/user/info")
    public String getUser() {
        return "用户模块接口";
    }
}

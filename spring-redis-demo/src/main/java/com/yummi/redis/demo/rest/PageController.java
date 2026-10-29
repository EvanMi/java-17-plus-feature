package com.yummi.redis.demo.rest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/bloom-test")
    public String bloomTestPage() {
        return "bloom-test";
    }

    @GetMapping("/cuckoo-test")
    public String cuckooTestPage() {
        return "cuckoo-test";
    }

    @GetMapping("/cms-test")
    public String cmsTestPage() {
        return "cms-test";
    }

    @GetMapping("/tdigest-test")
    public String tdigestTestPage() {
        return "tdigest-test";
    }

    @GetMapping("/topk-test")
    public String topkTestPage() {
        return "topk-test";
    }

    @GetMapping("/json-test")
    public String jsonTestPage() {
        return "json-test";
    }
}
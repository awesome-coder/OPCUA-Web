package com.opcua.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping({
        "/",
        "/devices",
        "/devices/**",
        "/tags",
        "/tags/**",
        "/browse",
        "/browse/**",
        "/live",
        "/live/**",
        "/bacnet",
        "/bacnet/**",
        "/settings",
        "/settings/**"
    })
    public String index() {
        return "forward:/index.html";
    }
}

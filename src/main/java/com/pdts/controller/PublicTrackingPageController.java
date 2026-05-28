package com.pdts.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PublicTrackingPageController {

    @GetMapping({"/portal", "/public", "/track"})
    public String publicTrackingPage() {
        return "public";
    }
}

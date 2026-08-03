package com.xai.dungeonmaster.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Convenience redirects into the bundled web SPA ({@code classpath:/static/app/}).
 * Prefer {@code /app/index.html} — directory {@code /app/} can 500 under some
 * resource-handler / welcome-page combinations.
 */
@Controller
public class SpaRedirectController {

    @GetMapping({"/app", "/app/", "/play", "/client"})
    public String app() {
        return "redirect:/app/index.html";
    }

    /** Optional entry from the server root when no other index is present. */
    @GetMapping("/")
    public String root() {
        return "redirect:/app/index.html";
    }
}

package com.xai.dungeonmaster.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Convenience redirects into the bundled web SPA ({@code classpath:/static/app/}).
 * Spring Boot already serves {@code /app/} and {@code /app/index.html} as static
 * resources; these mappings make bare roots discoverable.
 */
@Controller
public class SpaRedirectController {

    @GetMapping({"/app", "/play", "/client"})
    public String app() {
        return "redirect:/app/";
    }

    /** Optional entry from the server root when no other index is present. */
    @GetMapping("/")
    public String root() {
        return "redirect:/app/";
    }
}

package com.xai.dungeonmaster.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class SpaRedirectControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = standaloneSetup(new SpaRedirectController()).build();
    }

    @Test
    void rootRedirectsToApp() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/"));
    }

    @Test
    void playAndClientAliasesRedirect() throws Exception {
        mvc.perform(get("/play"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/"));
        mvc.perform(get("/client"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/"));
        mvc.perform(get("/app"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/"));
    }
}

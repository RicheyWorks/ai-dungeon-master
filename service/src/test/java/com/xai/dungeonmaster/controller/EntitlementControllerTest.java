package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.auth.JwtAuthFilter;
import com.xai.dungeonmaster.auth.JwtService;
import com.xai.dungeonmaster.auth.SessionService;
import com.xai.dungeonmaster.entitlement.EntitlementService;
import com.xai.dungeonmaster.plugin.builtin.DevStorefront;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Standalone MockMvc test for /v2/entitlements. The authenticated session is
 * injected as a request attribute (as JwtAuthFilter would), so a valid dev
 * receipt grants the product and shows up in the listing; a forged one is 402
 * and unauthenticated access is 401.
 */
class EntitlementControllerTest {

    private MockMvc mvc;
    private SessionService.Session session;

    @BeforeEach
    void setUp() {
        session = new SessionService(new JwtService("entitlement-test-secret-abcdefgh", 3600))
                .createSession("Kael").session();
        mvc = standaloneSetup(new EntitlementController(new EntitlementService())).build();
    }

    @Test
    void validReceiptGrantsAndListsProduct() throws Exception {
        String receipt = new DevStorefront().signReceipt("sku_gold");
        mvc.perform(post("/v2/entitlements/verify").contentType(APPLICATION_JSON)
                        .requestAttr(JwtAuthFilter.SESSION_ATTR, session)
                        .content("{\"storefront\":\"dev\",\"productId\":\"sku_gold\",\"receipt\":\"" + receipt + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("entitlement"))
                .andExpect(jsonPath("$.payload.granted").value(true))
                .andExpect(jsonPath("$.payload.owned", hasItem("sku_gold")));

        mvc.perform(get("/v2/entitlements").requestAttr(JwtAuthFilter.SESSION_ATTR, session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("entitlements"))
                .andExpect(jsonPath("$.payload.owned", hasItem("sku_gold")));
    }

    @Test
    void forgedReceiptReturns402() throws Exception {
        mvc.perform(post("/v2/entitlements/verify").contentType(APPLICATION_JSON)
                        .requestAttr(JwtAuthFilter.SESSION_ATTR, session)
                        .content("{\"storefront\":\"dev\",\"productId\":\"sku_bad\",\"receipt\":\"forged\"}"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.payload.granted").value(false));
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
        mvc.perform(get("/v2/entitlements")).andExpect(status().isUnauthorized());
    }
}

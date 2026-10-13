package com.splitwise.web;

import com.splitwise.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The React app served from the jar, next to the API. The test classpath carries a stand-in build
 * (src/test/resources/static) in place of frontend/dist.
 */
class WebAppIntegrationTest extends ApiTestSupport {

    @Test
    void theAppLoadsWithoutSigningIn() throws Exception {
        // Spring's welcome page forwards / to index.html (MockMvc records the forward instead of following it)
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Splitwise test app")));
    }

    @Test
    void aDeepLinkGetsTheAppSoItsRouterCanShowThePage() throws Exception {
        for (String page : new String[]{"/login", "/groups/12", "/groups/12/", "/recurring"}) {
            mvc.perform(get(page).queryParam("tab", "report"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("Splitwise test app")));
        }
    }

    @Test
    void theAppPageIsRecheckedOnEveryVisitButAssetsAreCachedForever() throws Exception {
        mvc.perform(get("/groups/12")).andExpect(header().string("Cache-Control", "no-cache"));
        mvc.perform(get("/assets/index-test.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("test app")))
                .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"));
    }

    @Test
    void aMissingFileIsA404NotTheApp() throws Exception {
        mvc.perform(get("/assets/index-gone.js")).andExpect(status().isNotFound());
        mvc.perform(get("/favicon.png")).andExpect(status().isNotFound());
    }

    @Test
    void theApiStaysBehindSignInAndUnknownApiPathsAre404s() throws Exception {
        mvc.perform(get("/api/groups")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/nope")).andExpect(status().isUnauthorized());

        Session alice = user("Alice");
        call(alice, get("/api/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No endpoint GET /api/nope"));
    }

    @Test
    void onlyReadingTheAppIsOpen() throws Exception {
        mvc.perform(post("/groups/12")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }
}

package com.splitwise.support;

import java.time.ZoneId;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.junit.jupiter.api.AfterEach;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for HTTP tests: the full application on H2 (Flyway, security, validation) plus small helpers to set up
 * users, groups, expenses and settlements through the real API. Every test creates its own users, so tests are
 * independent of each other and of execution order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSupport.ClockConfig.class)
public abstract class ApiTestSupport {

    /** Replaces the application clock with one the tests can move. */
    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(ZoneId.systemDefault());
        }
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected MutableClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Autowired
    protected ObjectMapper json;

    public record Session(long id, String email, String token) {
    }

    /** One participant of an expense: user id and the split value (ignored for EQUAL). */
    public record Share(long userId, String value) {
        public static Share of(Session session) {
            return new Share(session.id(), null);
        }

        public static Share of(Session session, String value) {
            return new Share(session.id(), value);
        }
    }

    // ------------------------------------------------------------------ users and groups

    protected Session user(String name) throws Exception {
        String email = name.toLowerCase().replace(" ", "") + "-" + UUID.randomUUID().toString().substring(0, 8)
                + "@example.com";
        JsonNode registered = read(mvc.perform(postJson("/api/auth/register",
                Map.of("name", name, "email", email, "password", "password123"))).andExpect(status().isCreated()));
        JsonNode login = read(mvc.perform(postJson("/api/auth/login", Map.of("email", email, "password", "password123")))
                .andExpect(status().isOk()));
        return new Session(registered.get("id").asLong(), email, login.get("accessToken").asText());
    }

    /** Creates a group owned by {@code owner} and adds the others. */
    protected long group(Session owner, Session... members) throws Exception {
        long groupId = read(call(owner, postJson("/api/groups", Map.of("name", "Group " + UUID.randomUUID())))
                .andExpect(status().isCreated())).get("id").asLong();
        for (Session member : members) {
            call(owner, postJson("/api/groups/" + groupId + "/members", Map.of("email", member.email())))
                    .andExpect(status().isOk());
        }
        return groupId;
    }

    // ------------------------------------------------------------------ expenses and settlements

    protected Map<String, Object> expenseBody(Long groupId, Session payer, String amount, String splitType,
                                              Share... shares) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("description", "Dinner");
        body.put("amount", new BigDecimal(amount));
        body.put("paidById", payer.id());
        if (groupId != null) {
            body.put("groupId", groupId);
        }
        body.put("splitType", splitType);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Share share : shares) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("userId", share.userId());
            if (share.value() != null) {
                s.put("value", new BigDecimal(share.value()));
            }
            list.add(s);
        }
        body.put("shares", list);
        return body;
    }

    protected ResultActions addExpense(Session caller, Map<String, Object> body) throws Exception {
        return call(caller, postJson("/api/expenses", body));
    }

    /** Adds an EQUAL group expense and returns its JSON. */
    protected JsonNode equalExpense(Session caller, long groupId, Session payer, String amount, Session... among)
            throws Exception {
        Share[] shares = new Share[among.length];
        for (int i = 0; i < among.length; i++) {
            shares[i] = Share.of(among[i]);
        }
        return read(addExpense(caller, expenseBody(groupId, payer, amount, "EQUAL", shares))
                .andExpect(status().isCreated()));
    }

    /** Body for PUT /api/expenses/{id}: a create body without groupId, plus the version. */
    protected Map<String, Object> updateBody(long version, Session payer, String amount, String splitType,
                                             Share... shares) {
        Map<String, Object> body = expenseBody(null, payer, amount, splitType, shares);
        body.put("description", "Dinner (edited)");
        body.put("version", version);
        return body;
    }

    protected ResultActions updateExpense(Session caller, long expenseId, Map<String, Object> body) throws Exception {
        return call(caller, put("/api/expenses/" + expenseId).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    protected ResultActions settle(Session caller, Long groupId, Session from, Session to, String amount)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fromUserId", from.id());
        body.put("toUserId", to.id());
        body.put("amount", new BigDecimal(amount));
        if (groupId != null) {
            body.put("groupId", groupId);
        }
        return call(caller, postJson("/api/settlements", body));
    }

    /** Net balance per user id in the group (positive = is owed money). */
    protected Map<Long, BigDecimal> balances(Session caller, long groupId) throws Exception {
        JsonNode list = read(call(caller, get("/api/groups/" + groupId + "/balances")).andExpect(status().isOk()));
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        list.forEach(b -> result.put(b.get("userId").asLong(), b.get("netBalance").decimalValue().setScale(2)));
        return result;
    }

    /** My overall balance in INR (0 when square). */
    protected BigDecimal myOverallBalance(Session caller) throws Exception {
        return myOverallBalance(caller, "INR");
    }

    protected BigDecimal myOverallBalance(Session caller, String currency) throws Exception {
        JsonNode balances = read(call(caller, get("/api/users/me/balance")).andExpect(status().isOk())).get("balances");
        for (JsonNode balance : balances) {
            if (balance.get("currency").asText().equals(currency)) {
                return balance.get("amount").decimalValue();
            }
        }
        return BigDecimal.ZERO;
    }

    protected ResultActions removeMember(Session caller, long groupId, Session member) throws Exception {
        return call(caller, delete("/api/groups/" + groupId + "/members/" + member.id()));
    }

    // ------------------------------------------------------------------ plumbing

    protected ResultActions call(Session session, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + session.token()));
    }

    protected MockHttpServletRequestBuilder postJson(String url, Object body) throws Exception {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    protected JsonNode read(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return body.isEmpty() ? json.nullNode() : json.readTree(body);
    }

    protected static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}

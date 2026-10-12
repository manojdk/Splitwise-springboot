package com.splitwise.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests through the full Spring context (security, validation, JPA, Flyway on H2).
 * Every test registers its own users with unique emails, so tests are independent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    // ------------------------------------------------------------------ auth

    @Test
    void registerThenLoginThenFetchProfile() throws Exception {
        String email = uniqueEmail("alice");
        mvc.perform(postJson("/api/auth/register", Map.of("name", "Alice", "email", email, "password", "password123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.password").doesNotExist());

        String body = mvc.perform(postJson("/api/auth/login", Map.of("email", email, "password", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(body).get("accessToken").asText();

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void duplicateEmailIsConflict() throws Exception {
        String email = uniqueEmail("dup");
        register("First", email);

        mvc.perform(postJson("/api/auth/register", Map.of("name", "Second", "email", email.toUpperCase(),
                        "password", "password123")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"));
    }

    @Test
    void invalidRegistrationIsBadRequestWithErrorBody() throws Exception {
        mvc.perform(postJson("/api/auth/register", Map.of("name", "", "email", "not-an-email", "password", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message", notNullValue()))
                .andExpect(jsonPath("$.path").value("/api/auth/register"));
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        String email = uniqueEmail("bob");
        register("Bob", email);

        mvc.perform(postJson("/api/auth/login", Map.of("email", email, "password", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void protectedEndpointsRequireAToken() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/users/me"));

        mvc.perform(get("/api/groups").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiDocsAndHealthArePublic() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/expenses']", notNullValue()));
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // ------------------------------------------------------------------ main flow

    @Test
    void groupExpenseBalanceSettleFlow() throws Exception {
        Session alice = registerAndLogin("Alice");
        Session bob = registerAndLogin("Bob");
        Session carol = registerAndLogin("Carol");

        long groupId = createGroup(alice, "Goa trip");
        addMember(alice, groupId, bob.email).andExpect(status().isOk());
        addMember(alice, groupId, carol.email)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members", hasSize(3)));
        addMember(alice, groupId, bob.email).andExpect(status().isConflict());

        // Alice pays 300, split equally -> 100 each
        mvc.perform(auth(postJson("/api/expenses", Map.of(
                        "description", "Hotel", "amount", "300.00", "paidById", alice.id, "groupId", groupId,
                        "splitType", "EQUAL",
                        "shares", new Object[]{Map.of("userId", alice.id), Map.of("userId", bob.id),
                                Map.of("userId", carol.id)})), alice))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.shares", hasSize(3)))
                .andExpect(jsonPath("$.shares[0].amount").value(100.00));

        // Bob pays 60, split 50% Alice / 50% Carol
        mvc.perform(auth(postJson("/api/expenses", Map.of(
                        "description", "Dinner", "amount", 60, "paidById", bob.id, "groupId", groupId,
                        "splitType", "PERCENTAGE",
                        "shares", new Object[]{Map.of("userId", alice.id, "value", 50),
                                Map.of("userId", carol.id, "value", 50)})), bob))
                .andExpect(status().isCreated());

        mvc.perform(auth(get("/api/groups/" + groupId + "/expenses"), carol))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));

        // Alice +170, Bob -40, Carol -130
        mvc.perform(auth(get("/api/groups/" + groupId + "/balances"), bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].userId").value(alice.id))
                .andExpect(jsonPath("$[0].netBalance").value(170.00))
                .andExpect(jsonPath("$[1].netBalance").value(-40.00))
                .andExpect(jsonPath("$[2].netBalance").value(-130.00));

        mvc.perform(auth(get("/api/groups/" + groupId + "/debts/simplified"), carol))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].fromUserId").value(carol.id))
                .andExpect(jsonPath("$[0].toUserId").value(alice.id))
                .andExpect(jsonPath("$[0].amount").value(130.00))
                .andExpect(jsonPath("$[1].fromUserId").value(bob.id))
                .andExpect(jsonPath("$[1].amount").value(40.00));

        // Carol settles up with Alice
        mvc.perform(auth(postJson("/api/settlements", Map.of(
                        "fromUserId", carol.id, "toUserId", alice.id, "amount", "130", "groupId", groupId)), carol))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(130.00));

        mvc.perform(auth(get("/api/groups/" + groupId + "/debts/simplified"), alice))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fromUserId").value(bob.id))
                .andExpect(jsonPath("$[0].amount").value(40.00));

        mvc.perform(auth(get("/api/groups/" + groupId + "/settlements"), bob))
                .andExpect(jsonPath("$.content", hasSize(1)));

        mvc.perform(auth(get("/api/users/me/balance"), alice))
                .andExpect(jsonPath("$.balances[0].currency").value("INR"))
                .andExpect(jsonPath("$.balances[0].amount").value(40.00));

        mvc.perform(auth(get("/api/users/me/expenses"), carol))
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    // ------------------------------------------------------------------ access control & validation

    @Test
    void nonMemberIsForbiddenFromGroupData() throws Exception {
        Session owner = registerAndLogin("Owner");
        Session outsider = registerAndLogin("Outsider");
        long groupId = createGroup(owner, "Private");

        mvc.perform(auth(get("/api/groups/" + groupId), outsider))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(auth(get("/api/groups/" + groupId + "/balances"), outsider))
                .andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/groups/" + groupId + "/debts/simplified"), outsider))
                .andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/groups/" + groupId + "/expenses"), outsider))
                .andExpect(status().isForbidden());
        addMember(outsider, groupId, outsider.email).andExpect(status().isForbidden());
        mvc.perform(auth(postJson("/api/expenses", Map.of(
                        "description", "Sneaky", "amount", "10", "paidById", outsider.id, "groupId", groupId,
                        "splitType", "EQUAL", "shares", new Object[]{Map.of("userId", outsider.id)})), outsider))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownGroupIsNotFound() throws Exception {
        Session user = registerAndLogin("Lonely");
        mvc.perform(auth(get("/api/groups/999999"), user))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void invalidSplitIsRejectedAndNothingIsSaved() throws Exception {
        Session a = registerAndLogin("A");
        Session b = registerAndLogin("B");
        long groupId = createGroup(a, "Flat");
        addMember(a, groupId, b.email).andExpect(status().isOk());

        mvc.perform(auth(postJson("/api/expenses", Map.of(
                        "description", "Groceries", "amount", "100", "paidById", a.id, "groupId", groupId,
                        "splitType", "EXACT",
                        "shares", new Object[]{Map.of("userId", a.id, "value", "60"),
                                Map.of("userId", b.id, "value", "30")})), a))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Exact shares add up to 90.00 but the expense amount is 100.00"));

        mvc.perform(auth(get("/api/groups/" + groupId + "/expenses"), a))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void participantOutsideGroupIsBadRequest() throws Exception {
        Session a = registerAndLogin("Member");
        Session stranger = registerAndLogin("Stranger");
        long groupId = createGroup(a, "Club");

        mvc.perform(auth(postJson("/api/expenses", Map.of(
                        "description", "Snacks", "amount", "20", "paidById", a.id, "groupId", groupId,
                        "splitType", "EQUAL",
                        "shares", new Object[]{Map.of("userId", a.id), Map.of("userId", stranger.id)})), a))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonGroupExpenseBetweenTwoFriends() throws Exception {
        Session a = registerAndLogin("Friend A");
        Session b = registerAndLogin("Friend B");

        mvc.perform(auth(postJson("/api/expenses", Map.of(
                        "description", "Cab", "amount", "100.01", "paidById", a.id, "splitType", "EQUAL",
                        "shares", new Object[]{Map.of("userId", a.id), Map.of("userId", b.id)})), a))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupId").doesNotExist());

        mvc.perform(auth(get("/api/users/me/balance"), b))
                .andExpect(jsonPath("$.balances[0].amount").value(-50.00));
        mvc.perform(auth(get("/api/users/me/balance"), a))
                .andExpect(jsonPath("$.balances[0].amount").value(50.00));
    }

    // ------------------------------------------------------------------ helpers

    private record Session(long id, String email, String token) {
    }

    private Session registerAndLogin(String name) throws Exception {
        String email = uniqueEmail(name.replace(" ", ""));
        long id = register(name, email);
        String body = mvc.perform(postJson("/api/auth/login", Map.of("email", email, "password", "password123")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Session(id, email, json.readTree(body).get("accessToken").asText());
    }

    private long register(String name, String email) throws Exception {
        String body = mvc.perform(postJson("/api/auth/register",
                        Map.of("name", name, "email", email, "password", "password123")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private long createGroup(Session owner, String name) throws Exception {
        String body = mvc.perform(auth(postJson("/api/groups", Map.of("name", name)), owner))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        return node.get("id").asLong();
    }

    private ResultActions addMember(Session caller, long groupId, String email) throws Exception {
        return mvc.perform(auth(postJson("/api/groups/" + groupId + "/members", Map.of("email", email)), caller));
    }

    private MockHttpServletRequestBuilder postJson(String url, Object body) throws Exception {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, Session session) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + session.token());
    }

    private static String uniqueEmail(String prefix) {
        return prefix.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }
}

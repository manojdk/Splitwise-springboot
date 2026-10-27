package com.splitwise.web;

import com.splitwise.service.CurrencyRules;
import com.splitwise.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Currency;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Yen and won have no smaller unit, so amounts in them are whole numbers: 100 yen among three is 34, 33, 33,
 * never 33.34. Currencies with three decimals (KWD) keep the app's two, as before.
 */
class WholeUnitCurrencyIntegrationTest extends ApiTestSupport {

    @Test
    void aYenExpenseIsSplitIntoWholeYenAndRefusesDecimals() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session carol = user("Carol");
        long tokyo = yenGroup(alice, bob, carol);

        addExpense(alice, expenseBody(tokyo, alice, "100.50", "EQUAL", Share.of(alice), Share.of(bob), Share.of(carol)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("JPY amounts have no decimals"));

        addExpense(alice, expenseBody(tokyo, alice, "100", "EQUAL", Share.of(alice), Share.of(bob), Share.of(carol)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shares[0].amount").value(34))
                .andExpect(jsonPath("$.shares[1].amount").value(33))
                .andExpect(jsonPath("$.shares[2].amount").value(33));

        addExpense(alice, expenseBody(tokyo, alice, "100", "EXACT", Share.of(alice, "50.5"), Share.of(bob, "49.5")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Amount for user " + alice.id() + " must be a whole number"));
    }

    @Test
    void yenPaymentsAndNonGroupExpensesAreWholeToo() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long tokyo = yenGroup(alice, bob);

        call(bob, postJson("/api/settlements", Map.of("fromUserId", bob.id(), "toUserId", alice.id(),
                "amount", new BigDecimal("10.50"), "groupId", tokyo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("JPY amounts have no decimals"));
        call(bob, postJson("/api/settlements", Map.of("fromUserId", bob.id(), "toUserId", alice.id(),
                "amount", new BigDecimal("10"), "groupId", tokyo)))
                .andExpect(status().isCreated());

        Map<String, Object> won = expenseBody(null, alice, "1000.5", "EQUAL", Share.of(alice), Share.of(bob));
        won.put("currency", "KRW");
        addExpense(alice, won).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("KRW amounts have no decimals"));
    }

    @Test
    void otherCurrenciesKeepTwoDecimals() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        contacts(alice, bob);

        for (String currency : new String[] {"INR", "KWD"}) {
            Map<String, Object> body = expenseBody(null, alice, "100.01", "EQUAL", Share.of(alice), Share.of(bob));
            body.put("currency", currency);
            addExpense(alice, body).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.shares[0].amount").value(50.01));
        }
    }

    /** The web app keeps its own copy of the list (for the live preview); it must be the same as Java's. */
    @Test
    void theWebAppKnowsTheSameCurrenciesWithoutDecimals() throws Exception {
        String source = Files.readString(Path.of("frontend", "src", "lib", "currencies.ts"), StandardCharsets.UTF_8);
        Matcher list = Pattern.compile("NO_DECIMALS[^=]*=\\s*\\[([^\\]]*)]").matcher(source);
        assertThat(list.find()).as("NO_DECIMALS in currencies.ts").isTrue();
        Set<String> web = Arrays.stream(list.group(1).split(","))
                .map(s -> s.replaceAll("[^A-Z]", ""))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));

        Set<String> java = Currency.getAvailableCurrencies().stream()
                .map(Currency::getCurrencyCode)
                .filter(code -> CurrencyRules.decimals(code) == 0)
                .collect(Collectors.toCollection(TreeSet::new));
        assertThat(web).isEqualTo(java);
    }

    private long yenGroup(Session owner, Session... members) throws Exception {
        long groupId = read(call(owner, postJson("/api/groups", Map.of("name", "Tokyo", "currency", "JPY")))
                .andExpect(status().isCreated())).get("id").asLong();
        for (Session member : members) {
            call(owner, postJson("/api/groups/" + groupId + "/members", Map.of("email", member.email())))
                    .andExpect(status().isOk());
        }
        return groupId;
    }
}

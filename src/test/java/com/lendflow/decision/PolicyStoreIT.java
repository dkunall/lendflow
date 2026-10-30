package com.lendflow.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lendflow.decision.application.ActivePolicy;
import com.lendflow.decision.application.PolicyCatalog;
import com.lendflow.shared.domain.Product;
import com.lendflow.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DataAccessException;

class PolicyStoreIT extends IntegrationTest {

    @Autowired
    PolicyCatalog catalog;

    @Autowired
    ApplicationRunner policySeeder;

    @Test
    void seedsOnePublishedVersionPerProductWithItsScorecards() {
        ActivePolicy personal = catalog.published(Product.PERSONAL);
        ActivePolicy home = catalog.published(Product.HOME);

        assertThat(personal.ruleset().key()).isEqualTo("PERSONAL-v1");
        assertThat(personal.champion().id()).isEqualTo("PL-SC-1");
        assertThat(personal.challengerScorecard()).map(c -> c.id()).contains("PL-SC-2");
        // RulesetIT may already have published an unchanged copy of the home policy as a later version.
        assertThat(home.ruleset().key()).startsWith("HOME-v");
        assertThat(home.champion().id()).isEqualTo("HL-SC-1");
        assertThat(home.challengerScorecard()).isEmpty();
    }

    @Test
    void seedingAgainChangesNothing() throws Exception {
        long before = jdbc.sql("SELECT count(*) FROM rulesets").query(Long.class).single();
        ApplicationArguments none = new DefaultApplicationArguments();

        policySeeder.run(none);

        assertThat(jdbc.sql("SELECT count(*) FROM rulesets").query(Long.class).single()).isEqualTo(before);
    }

    @Test
    void publishedRulesetsAndScorecardsCannotBeEditedInPlace() {
        assertThatThrownBy(() -> jdbc.sql("""
                UPDATE rulesets SET definition = jsonb_set(definition, '{parameters,foirCap}', '"0.9"')
                WHERE product = 'PERSONAL' AND status = 'PUBLISHED'
                """).update())
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("cannot be edited");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM scorecards WHERE id = 'PL-SC-1'").update())
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("immutable");
        assertThat(catalog.published(Product.PERSONAL).ruleset().parameters().foirCap()).isEqualByComparingTo("0.50");
    }
}

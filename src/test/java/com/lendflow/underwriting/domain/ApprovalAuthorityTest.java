package com.lendflow.underwriting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendflow.shared.domain.Actor.Role;
import com.lendflow.shared.domain.Product;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApprovalAuthorityTest {

    private final ApprovalAuthority chain = ApprovalAuthority.chain(
        Map.of(Product.PERSONAL, new BigDecimal("1500000"), Product.HOME, new BigDecimal("10000000")));

    @Test
    void managerSignsOffUpToTheProductLimitAndTheHeadAboveIt() {
        assertThat(chain.checkerFor(Product.PERSONAL, new BigDecimal("1500000"))).isEqualTo(Role.CREDIT_MANAGER);
        assertThat(chain.checkerFor(Product.PERSONAL, new BigDecimal("1500001"))).isEqualTo(Role.CREDIT_HEAD);
        assertThat(chain.checkerFor(Product.HOME, new BigDecimal("9000000"))).isEqualTo(Role.CREDIT_MANAGER);
        assertThat(chain.checkerFor(Product.HOME, new BigDecimal("12000000"))).isEqualTo(Role.CREDIT_HEAD);
    }

    @Test
    void aDeclineHasNoAmountAndGoesToTheFirstChecker() {
        assertThat(chain.checkerFor(Product.HOME, null)).isEqualTo(Role.CREDIT_MANAGER);
    }

    @Test
    void higherRolesCoverLowerQueuesButNotTheReverse() {
        assertThat(Authority.covers(Set.of(Role.CREDIT_HEAD), Role.UNDERWRITER)).isTrue();
        assertThat(Authority.covers(Set.of(Role.UNDERWRITER), Role.CREDIT_MANAGER)).isFalse();
        assertThat(Authority.covers(Set.of(Role.OPS, Role.POLICY_ADMIN), Role.UNDERWRITER)).isFalse();
        assertThat(Authority.claimable(Set.of(Role.CREDIT_MANAGER)))
            .containsExactly(Role.UNDERWRITER, Role.CREDIT_MANAGER);
        assertThat(Authority.above(Role.UNDERWRITER)).isEqualTo(Role.CREDIT_MANAGER);
        assertThat(Authority.above(Role.CREDIT_HEAD)).isEqualTo(Role.CREDIT_HEAD);
    }
}

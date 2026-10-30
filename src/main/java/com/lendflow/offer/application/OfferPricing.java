package com.lendflow.offer.application;

import com.lendflow.offer.domain.HomeOfferGenerator;
import com.lendflow.offer.domain.OfferGenerator;
import com.lendflow.offer.domain.PersonalOfferGenerator;
import com.lendflow.shared.domain.Product;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class OfferPricing {

    private final Map<Product, OfferGenerator> generators = new EnumMap<>(Product.class);

    OfferPricing(@Value("${lendflow.offers.gst-percent:18}") BigDecimal gstPercent,
                 @Value("${lendflow.offers.personal.minimum-fee:1000}") BigDecimal personalMinimumFee,
                 @Value("${lendflow.offers.personal.valid-for:7d}") Duration personalValidity,
                 @Value("${lendflow.offers.home.fee-cap:25000}") BigDecimal homeFeeCap,
                 @Value("${lendflow.offers.home.valid-for:30d}") Duration homeValidity) {
        generators.put(Product.PERSONAL, new PersonalOfferGenerator(gstPercent, personalMinimumFee, personalValidity));
        generators.put(Product.HOME, new HomeOfferGenerator(gstPercent, homeFeeCap, homeValidity));
    }

    OfferGenerator forProduct(Product product) {
        return generators.get(product);
    }
}

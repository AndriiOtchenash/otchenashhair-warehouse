package com.hairmony.warehouse.web.formatter;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component("qf")
public class QuantityFormatter {

    public String format(BigDecimal quantity, String unit) {
        if (quantity == null) return "0";
        if ("PCS".equals(unit)) {
            return String.valueOf(quantity.intValue());
        }
        return quantity.stripTrailingZeros().toPlainString();
    }
}

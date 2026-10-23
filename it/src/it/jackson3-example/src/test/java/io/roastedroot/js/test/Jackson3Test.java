package io.roastedroot.js.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.roastedroot.quickjs4j.annotations.ScriptInterface;
import io.roastedroot.quickjs4j.jackson3.Jackson3Codec;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class Jackson3Test {

    public record Item(String sku, int quantity, double unitPrice) {}

    public record Order(String id, List<Item> items) {}

    public record Total(String orderId, double amount) {}

    public static class Prices {
        public double discount(String sku) {
            return sku.startsWith("SALE") ? 0.5 : 1.0;
        }
    }

    @ScriptInterface(context = Prices.class)
    public interface Pricing {
        Total total(Order order);
    }

    private static final Order ORDER =
            new Order("o-1", List.of(new Item("SALE-1", 2, 10.0), new Item("FULL-1", 1, 5.0)));

    private static String script(String unitPrice, String orderId) {
        return "function total(order) {\n"
                + "  const amount = order.items.reduce((acc, item) =>\n"
                + "    acc + item.quantity * item."
                + unitPrice
                + " * Pricing_Builtins.discount(item.sku), 0);\n"
                + "  return { "
                + orderId
                + ": order.id, amount };\n"
                + "}\n";
    }

    @Test
    public void discoveredCodec() {
        try (var pricing = new Pricing_Proxy(script("unitPrice", "orderId"), new Prices())) {
            assertEquals(new Total("o-1", 15.0), pricing.total(ORDER));
        }
    }

    @Test
    public void applicationMapper() {
        var mapper =
                JsonMapper.builder()
                        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                        .build();

        try (var pricing =
                new Pricing_Proxy(
                        script("unit_price", "order_id"),
                        new Prices(),
                        new Jackson3Codec(mapper))) {
            assertEquals(new Total("o-1", 15.0), pricing.total(ORDER));
        }
    }
}

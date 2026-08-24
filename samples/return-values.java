import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry;
import org.autojs.plugin.jvmsource.api.JvmScriptContext;

public final class Main implements AutoJsJvmEntry {
    @Override
    public Object run(JvmScriptContext context) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nullValue", null);
        result.put("boolean", true);
        result.put("integers", Arrays.asList(
                (byte) 1,
                (short) 2,
                3,
                4L,
                new BigInteger("12345678901234567890")));
        result.put("decimals", Arrays.asList(
                1.25F,
                2.5D,
                new BigDecimal("3.75")));
        result.put("text", new StringBuilder("hello"));
        result.put("character", '中');
        result.put("primitiveArray", new int[] {5, 6});
        result.put("objectArray", new Object[] {"x", false});

        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("items", Arrays.asList(7, 8));
        result.put("nested", nested);
        return result;
    }
}

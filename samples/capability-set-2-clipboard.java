import java.util.LinkedHashMap;
import java.util.Map;
import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry;
import org.autojs.plugin.jvmsource.api.JvmScriptContext;

public final class Main implements AutoJsJvmEntry {
    @Override
    public Object run(JvmScriptContext context) {
        String original = context.clipboard().getText();
        String marker = "M9 clipboard 你好";
        boolean restored = false;
        try {
            context.clipboard().setText(marker);
            String observed = context.clipboard().getText();
            context.clipboard().setText(original);
            restored = original.equals(context.clipboard().getText());

            context.console().log(
                    "clipboard round-trip: " + observed + "; restored=" + restored);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("before", original);
            result.put("written", observed);
            result.put("restored", restored);
            return result;
        } finally {
            if (!restored) {
                context.clipboard().setText(original);
            }
        }
    }
}

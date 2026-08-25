import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry;
import org.autojs.plugin.jvmsource.api.JvmScriptContext;

public final class Main implements AutoJsJvmEntry {
    @Override
    public Object run(JvmScriptContext context) {
        Map<String, Object> args = context.args();
        Map<?, ?> nested = (Map<?, ?>) args.get("nested");
        List<?> tags = (List<?>) args.get("tags");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", args.get("name"));
        result.put("enabled", args.get("enabled"));
        result.put("count", nested.get("count"));
        result.put("firstTag", tags.get(0));
        result.put("tagCount", tags.size());
        result.put("nullValue", args.get("nullable"));
        return result;
    }
}

import java.util.Arrays;
import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry;
import org.autojs.plugin.jvmsource.api.JvmScriptContext;

public final class Main implements AutoJsJvmEntry {
    @Override
    public Object run(JvmScriptContext context) {
        char[] payload = new char[65_536];
        Arrays.fill(payload, 'x');
        return new String(payload);
    }
}

import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry;
import org.autojs.plugin.jvmsource.api.JvmScriptContext;

public final class Main implements AutoJsJvmEntry {
    @Override
    public Object run(JvmScriptContext context) throws Exception {
        context.console().log("cancellation sample: sleeping");
        context.sleep(120_000L);
        context.console().log("cancellation sample: unexpected completion");
        return "not-cancelled";
    }
}

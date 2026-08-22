package samples.m5;

import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry;
import org.autojs.plugin.jvmsource.api.JvmScriptContext;

public final class Main implements AutoJsJvmEntry {
    @Override
    public Object run(JvmScriptContext context) throws Exception {
        long startedAt = System.currentTimeMillis();
        context.console().log("M5 Java log: 你好");
        context.toast("M5 Java toast");
        context.sleep(500L);
        context.console().error(
                "M5 Java error after " + (System.currentTimeMillis() - startedAt) + " ms");
        return 5;
    }
}

import java.time.LocalDate;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry;
import org.autojs.plugin.jvmsource.api.JvmScriptContext;

public final class Main implements AutoJsJvmEntry {
    @Override
    public Object run(JvmScriptContext context) {
        List<Integer> values = Stream.iterate(
                        0,
                        new Predicate<Integer>() {
                            @Override
                            public boolean test(Integer value) {
                                return value < 1;
                            }
                        },
                        new UnaryOperator<Integer>() {
                            @Override
                            public Integer apply(Integer value) {
                                return value + 1;
                            }
                        })
                .toList();
        LocalDate nextDay = LocalDate.of(2024, 2, 29).plusDays(values.size());
        return nextDay + ":CORE";
    }
}

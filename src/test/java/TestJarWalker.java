
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mofokom.jarwalker.JarWalker;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 *
 * @author wozza
 */
public class TestJarWalker {

    @Test
    public void testTranslate() {

        LinkedHashMap<String, List<List<String>>> in = new LinkedHashMap<>();

        in.put("1", Arrays.asList(Arrays.asList("A", "B", "C")));
        in.put("2", Arrays.asList(Arrays.asList("D", "B", "C")));
        in.put("3", Arrays.asList(Arrays.asList("E", "F", "C")));
        in.put("4", Arrays.asList(Arrays.asList("E", "F", "C")));

        LinkedHashMap<String, Object> ex = new LinkedHashMap<>();

        ex.put("[C B A]", Arrays.asList("1"));
        ex.put("[C B D]", Arrays.asList("2"));
        ex.put("[C F E]", Arrays.asList("3", "4"));

        Map<String, Object> out = JarWalker.translateBindings(in);

        assertEquals(ex.toString(), out.toString());

    }
}

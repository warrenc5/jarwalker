
import java.io.IOException;
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
    public void run() throws IOException, InterruptedException { 
        JarWalker.main(new String[]{
            "./test-jars/test.zip",
            "-m",
            "LocalStrings",
            "-p",
            "-z",
            "-v"
        });
    }
    
    @Test 
    public void runDelete() throws IOException, InterruptedException { 
        JarWalker.main(new String[]{
            "./test-jars",
            "-m",
            "log4j",
            "-d",
            "-d",
            "-v"
        });
    }

    @Test 
    public void runUpdate() throws IOException, InterruptedException { 
        JarWalker.main(new String[]{
            "./test-jars",
            "-m",
            "test-file.txt",
            "-u",
            "-v"
        });
    }
    
    @Test
    public void testTranslate() {

        LinkedHashMap<String, List<List<String>>> in = new LinkedHashMap<>();

        in.put("1", Arrays.asList(Arrays.asList("A", "B", "C")));
        in.put("2", Arrays.asList(Arrays.asList("D", "B", "C")));
        in.put("3", Arrays.asList(Arrays.asList("E", "F", "C")));
        in.put("4", Arrays.asList(Arrays.asList("E", "F", "C")));

        LinkedHashMap<String, Object> ex = new LinkedHashMap<>();

        ex.put("C/B/A", Arrays.asList("1"));
        ex.put("C/B/D", Arrays.asList("2"));
        ex.put("C/F/E", Arrays.asList("3", "4"));

        Map<String, Object> out = JarWalker.translateBindings(in);

        System.out.println(out);
        System.out.println(ex);
        assertEquals(ex.toString(), out.toString());

    }

    @Test
    public void tryfinally() {
        try {
            throw new NullPointerException();
        } catch (Throwable t) {
        } finally {
            System.out.println("finally");
        }
    }
}

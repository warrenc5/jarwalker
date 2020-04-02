package mofokom.jarwalker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * Hello world!
 *
 */
public class JarWalker {

    static boolean recursive = false;
    static List<String> match = new ArrayList<String>();
    private static boolean cat;
    private static boolean contents;
    private static boolean duplicates;
    private static boolean detectDuplicateJars;
    private static List<String> r;
    private static Map<String, Collection<String>> results;
    private static String jarContent;
    private static File parent;
    private static boolean debug;
    private static boolean verbose;
    private static boolean group;

    private static Queue q;

    public static void main(String[] args) throws IOException, InterruptedException {
        q = new ArrayDeque();

        if (args.length == 0) {
            usage();
        }

        results = new HashMap<>();

        List<File> l = new ArrayList<File>();

        File[] list;
        File d;

        for (int i = 0; i < args.length; i++) {

            String s = args[i];
            if (s.equals("-j")) {
                detectDuplicateJars = true;
            }
            if (s.equals("-d")) {
                duplicates = true;
                r = new ArrayList<String>(10000);
            }
            if (s.equals("-c")) {
                contents = true;
            }
            if (s.equals("-o")) {
                cat = true;
            }
            if (s.equals("-v")) {
                if (verbose == true) {
                    debug = true;
                }
                verbose = true;
            }
            if (s.equals("--help")) {
                usage();
            }
            if (s.equals("-g")) {
                group = true;
            } else if (s.equals("-r")) {
                recursive = true;
            } else if (s.equals("-m")) {
                match.add("^.*" + args[++i] + ".*$");
            } else {
                l.add(new File(s));
            }
        }
        if (l.isEmpty() && recursive) {
            l.add(new File("."));
        }

        if (verbose) {
            System.err.println("looking for " + match.toString());
        }

        processFileList(l);
        printResults();
    }

    private static void processFileList(List<File> list) throws IOException, InterruptedException {
        for (File f : list) {

            if (f.isDirectory()) {
                File[] fs = f.listFiles(new FilenameFilter() {

                    public boolean accept(File dir, String name) {
                        return name.matches(".*\\..ar$") || dir.isDirectory() && recursive;
                    }
                });
                if (fs != null && fs.length > 0) {
                    processFileList(Arrays.asList(fs));
                }
            } else if (f.getName().matches(".*\\..ar$")) {
                if (match != null && matches(f.getName())) {
                    jarContent = f.getName();
                }

                if (!f.getAbsoluteFile().getParentFile().equals(parent)) {
                    if (verbose) {
                        System.err.println("-" + (parent = f.getAbsoluteFile().getParentFile()).getAbsolutePath());
                    }

                }
                if (verbose) {
                    System.err.println("--" + f.getName());
                }
                try {
                    printEntries(0, new FileInputStream(f), f.getAbsolutePath());
                } catch (Exception ex) {
                    System.err.println(ex.getMessage());
                }
            }
        }
    }

    private static void printEntries(int depth, InputStream is, String name) throws IOException, InterruptedException {
        q.offer(name);
        final JarInputStream jis = new JarInputStream(is);
        JarEntry entry = null;
        depth++;
        while ((entry = jis.getNextJarEntry()) != null) {

            if (entry.getName().matches(".*\\..ar$")) {
//                if (match == null) {

                if (match != null && matches(entry.getName())) {
                    jarContent = entry.getName();
                    addResult(entry, q.toString());
                }

                if (debug) {
                    printSpace(depth);
                    System.err.println("-+ " + entry.getName() + " " + entry.getSize());
                }
                //               }

                final JarEntry e1 = entry;

                ByteArrayOutputStream pos = new ByteArrayOutputStream();

                //final JarOutputStream jos = new JarOutputStream(pos);
                //jos.putNextEntry(e1);
                byte[] buf = new byte[512];

                while (jis.available() > 0) {
                    int p = jis.read(buf);
                    if (p > 0) {
                        pos.write(buf, 0, p);
                    }
                    //System.out.println(jis.available() + " " + p);
                }
                //jos.close();

                ByteArrayInputStream pis = new ByteArrayInputStream(pos.toByteArray());

                printEntries(++depth, pis, entry.getName());

                if (!match.isEmpty() && matches(entry.getName())) {
                    jarContent = entry.getName();
                    addResult(entry, name);
                }

                depth--;
            } else if (!entry.isDirectory()) {
                if (duplicates && (match.isEmpty() || matches(entry.getName()))) {

                    /*
                    if (r.contains(entry.getName())) {
                        if (out) {
                            System.out.println("++ " + entry.getName() + " " + entry.getSize());
                        }
                    } else {
                        r.add(entry.getName());
                     */
                    addResult(entry, q.toString());
                } else;

                if (contents || name.equals(jarContent)) {
                    if (verbose) {
                        printSpace(depth);
                        System.out.println(entry.getName() + " " + entry.getSize());
                    }
                }

                if (!match.isEmpty() && matches(entry.getName())) {
                    if (debug) {
                        printSpace(depth);
                        System.err.println("-* " + entry.getName() + " " + entry.getSize());
                    }
                    addResult(entry, q.toString());

                    if (cat) {
                        printContents(jis);
                    }
                }
            }

        }
        jarContent = null;
        q.remove();
    }

    private static void printSpace(int depth) {
        for (int i = 0; i < depth * 2; i++) {
            System.out.print(' ');
        }
    }

    private static void printContents(JarInputStream jis) throws IOException {
        byte[] buf = new byte[512];
        while (jis.available() > 0) {
            int p = jis.read(buf);
            if (p > 0) {
                System.out.print(new String(buf, 0, p));
            }
            //System.out.println(jis.available() + " " + p);
        }

    }

    private static void usage() {
        System.err.println("Usage -j [-m regexp] [-c] [-d] [-o] directory|jar|ear|war ...");
        System.err.println("-j detect duplicate jars files");
        System.err.println("-r recursive for directories");
        System.err.println("-m regexp to match");
        System.err.println("-c show contents of jar files");
        System.err.println("-d show duplicates or exit after first");
        System.err.println("-o show contents");
        System.err.println("-v verbose");
        System.exit(1);
    }

    private static boolean matches(String name) {

        boolean m = false;

        for (String s : match) {
            if (!m) {
                m = name.matches(s);
            }
        }
        return m;

    }

    private static void addResult(JarEntry entry, String name) {

        if (checkForDuplicateFile(entry, name)) {
            if (detectDuplicateJars) {
                name = name + "*+";
            } else {
                name = name + "**";
            }
        }

        Collection<String> col = results.getOrDefault(entry.getName(), new HashSet());

        if (col == null || !col.contains(name)) {
            col.add(name);
        }
    }

    private static void printResults() throws IOException {

        if (group) {
            Map<String, Set<String>> r = new HashMap<String, Set<String>>();
            for (String k : results.keySet()) {
                Collection<String> col = results.getOrDefault(k, new HashSet());
                col.forEach(v -> {
                    Set<String> s = r.computeIfAbsent(v, (v1) -> new HashSet<String>());
                    s.add(k);
                });
            }
            System.out.println(r.toString().replaceAll("[\\{\\[,]", "\n"));
        } else {
            System.out.println(results.toString());//.replaceAll("[\\{\\[,]", "\n"));
        }
    }

    private static boolean checkForDuplicateFile(JarEntry entry, String name) {

        File f = new File(name);

        if (!f.exists()) {
            return false;
        }

        if (results.get(entry) == null) {
            return false;
        }

        for (String s : results.get(entry)) {
            File e = new File(s);

            if (e.exists() && f.getName().equals(e.getName()) && f.length() == e.length() && f.lastModified() == e.lastModified()) {
                return true;
            }
        }

        return false;
    }

}

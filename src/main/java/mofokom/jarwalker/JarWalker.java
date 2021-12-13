package mofokom.jarwalker;

import au.com.devnull.graalson.GraalsonProvider;
import au.com.devnull.graalson.JsonObjectBindings;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import static java.util.stream.Collectors.toList;
import javax.json.spi.JsonProvider;

/**
 * Hello world!
 *
 */
public class JarWalker {

    private static final String ARCHIVE = ".*\\..ar$";
    private static Deque<Path> stack = new ArrayDeque<Path>();
    static boolean recursive = false;
    static List<String> match = new ArrayList<String>();
    static List<String> excludes = new ArrayList<String>();
    private static boolean cat;
    private static boolean contents;
    private static boolean duplicates;
    private static boolean detectDuplicateJars;
    private static List<String> r;
    private static Map<String, List<List<String>>> results;
    private static String jarContent;
    private static File parent;
    private static boolean debug;
    private static boolean verbose;
    private static boolean group = true;
    private static Map<JarEntry, InputStream> entries = new HashMap<>();
    private static Map<JarEntry, Long> checkSum = new HashMap<>();
    private static boolean delete = false;
    private static int MAX_BUFFER = 1024 * 1024; //1Mb

    private static Map config = new HashMap<>();

    public static void main(String[] args) throws IOException, InterruptedException {
        config.put("spaces", Integer.valueOf(4));
        ((GraalsonProvider) JsonProvider.provider()).getConfigInUse().putAll(config);

        if (args.length <= 1) {
            usage();
            return;
        }

        results = new LinkedHashMap<>();

        List<File> l = new ArrayList<File>();

        File[] list;
        File d;

        for (int i = 0; i < args.length; i++) {

            String s = args[i];
            switch (s) {
                case "-j":
                    detectDuplicateJars = true;
                    /*
                    } else if (s.equals("-d")) {
                    duplicates = true;
                    r = new ArrayList<String>(10000);
                     */ break;
                case "-c":
                    contents = true;
                    break;
                case "-o":
                    cat = true;
                    break;
                case "-v":
                    if (verbose == true) {
                        debug = true;
                    }
                    verbose = true;
                    break;
                case "--help":
                case "-h":
                case "-?":
                    usage();
                    break;
                case "-f":
                    group = false;
                    break;
                case "-d":
                    delete = true;
                    break;
                case "-r":
                    recursive = true;
                    break;
                case "-x":
                    excludes.add("^.*" + args[++i] + ".*$");
                    break;
                case "-m":
                    match.add("^.*" + args[++i] + ".*$");
                    break;
                default:
                    l.add(new File(s));
                    break;
            }
        }

        if (l.isEmpty() && recursive) {
            l.add(new File("."));
        }

        if (verbose) {
            System.err.println(String.format("looking for %s in %s", match.toString(), l.toString()));
        }

        JarWalker.walk(l);
        printResults();
    }

    private static void walk(List<File> list) throws IOException, InterruptedException {
        if (debug) {
            System.err.println("depth " + stack.size());
        }
        for (File f : list) {

            stack.push(Path.of(f.getAbsolutePath()));

            if (excludes(f.getName())) {

            } else if (f.isDirectory()) {
                JarWalker.walk(f);
            } else if (f.getName().matches(ARCHIVE)) {
                if (!f.exists()) {
                    System.err.println("skipping missing file " + f.getPath() + " " + f.getAbsolutePath());
                } else {
                    showEntry(f);

                    if (delete) {
                        dirtyWalk(f);
                    } else {
                        try {
                            walk(null, new FileInputStream(f), f.getAbsolutePath());
                        } catch (Exception x) {
                            System.err.println("err ." + x.getMessage() + " " + f.getAbsolutePath());
                            if (verbose) {
                                x.printStackTrace();
                            }
                        }
                    }
                }

            }
            stack.pop();
        }
    }

    private static boolean walk(OutputStream os, InputStream is, String path) throws IOException, InterruptedException {

        final JarInputStream jis = new JarInputStream(is);
        Manifest manifest = jis.getManifest();
        JarOutputStream jos = null;
        if (delete) {

            if (manifest == null) {
                jos = new JarOutputStream(os);
            } else {
                jos = new JarOutputStream(os, manifest);
            }
        }

        boolean dirty = false;
        //try (JarOutputStream jos = (manifest == null) ? new JarOutputStream(os) : new JarOutputStream(os, manifest);) {
        try {
            JarEntry entry = null;

            while ((entry = jis.getNextJarEntry()) != null) {
                if (debug) {
                    System.err.println("processing " + entry.toString());
                }
                boolean write = true;

                try {

                    checkSum.put(entry, entry.getCrc());

                    if (excludes(entry.getName())) {
                        continue;
                    }

                    if (debug) {
                        showEntry(entry);
                    }

                    if (matches(entry.getName())) {
                        hit(entry);

                        if (delete) {
                            System.out.println(String.format("deleting %1$s in %2$s", entry.getName(), path));
                            write = false;
                            dirty = true;
                        }

                        if (contents) {
                            //TODO InputStream njis = readJarEntry(bis);
                        }
                    }

                    if (entry.isDirectory()) {
                        continue;

                    } else if (entry.getName().matches(ARCHIVE)) {

                        if (verbose) {
                            showEntry(entry);
                        }
                        stack.push(Path.of(entry.getRealName()));

                        if (delete) {
                            entry.setLastModifiedTime(FileTime.from(Instant.now()));
                            File temp = File.createTempFile(entry.getName() + ".", ".tmp");
                            temp.deleteOnExit();
                            FileOutputStream fos = new FileOutputStream(temp);

                            JarOutputStream njos = new JarOutputStream(fos);
                            njos.putNextEntry(entry);
                            dirty = dirty || walk(njos, jis, entry.getName());
                            njos.flush();
                            njos.close();

                            if (dirty) {
                                System.err.println(String.format("writing %1$s to %2$s", entry.getName(), temp.getAbsolutePath()));
                                JarInputStream njis = new JarInputStream(new FileInputStream(temp));
                                jos.putNextEntry(njis.getNextJarEntry());
                                jos.write(njis.readAllBytes());
                                jos.closeEntry();
                            }
                        } else {
                            walk(null, jis, entry.getName());
                        }
                        stack.pop();

                    } else {
                        if (delete && write) {
                            copy(entry, path, jis, jos);
                        }

                        if (cat) {
                            // entries.put(entry, jis);
                        }
                    }

                } finally {

                }
            }
        } finally {
            if (delete && jos != null) {
                jos.close();
            }
        }
        return dirty;
    }

    static int lastDepth = 0;

    private static String space(int depth) {
        StringBuilder builder = new StringBuilder();
        if (depth > 0) {
            for (int i = 0; i < depth * 4; i++) {
                builder.append(' ');
            }
        }
        return builder.toString();
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
        System.err.println("-f flat output. don't group by jar file");
        System.err.println("-m regexp to match");
        System.err.println("-c show contents of jar files");
        //System.err.println("-d show duplicates or exit after first");
        System.err.println("-o show contents");
        System.err.println("-x exclude regex");
        System.err.println("-v verbose, -v -v even more verbose");
        System.err.println("-h help");
        System.exit(1);
    }

    private static boolean excludes(String name) {
        return matches(excludes, name);
    }

    private static boolean matches(String name) {
        return matches(match, name);
    }

    private static boolean matches(List<String> match, String name) {

        boolean m = false;

        for (String s : match) {
            if (!m) {
                m = name.matches(s);
            }
        }
        return m;

    }

    private static void addResult(JarEntry entry, List<Path> paths) {

        if (verbose) {
            System.out.println("found " + entry.getName() + " " + paths.toString());
        }

        List<List<String>> jars = results.getOrDefault(entry.getName(), new ArrayList());
        jars.add(paths.stream().filter(p -> p.getFileName().toString().matches(ARCHIVE)).map(f -> f.toString()).collect(toList()));
        results.put(entry.getName(), jars);
    }

    private static void printResults() throws IOException {

        Map<String, Object> bindings = translateBindings(results);
        System.out.println(new JsonObjectBindings(bindings).stringify());

    }

    /**
     * private static boolean checkForDuplicateFile(JarEntry entry, String name)
     * { File f = new File(name);
     *
     * if (!f.exists()) { return false; }
     *
     * if (results.get(entry) == null) { return false; }
     *
     * for (String s : results.get(entry)) { File e = new File(s);
     *
     * if (e.exists() && f.getName().equals(e.getName()) && f.length() ==
     * e.length() && f.lastModified() == e.lastModified()) { return true; } }
     *
     * return false; }
     *
     */
    private static void walk(File f) throws IOException, InterruptedException {
        File[] fs = f.listFiles(new FilenameFilter() {

            public boolean accept(File dir, String name) {
                boolean accept = name.matches(ARCHIVE) || (dir.isDirectory() && recursive);
                if (debug) {
                    System.err.println(String.format("%1$s, %2$s, accepted: %3$s", dir.getAbsolutePath(), name, accept));
                }
                return accept;
            }
        });
        if (fs != null && fs.length > 0) {
            walk(Arrays.asList(fs));
        }
    }

    private static InputStream readJarEntry(JarInputStream jis) throws IOException {
        ByteArrayOutputStream pos = new ByteArrayOutputStream();

        byte[] buf = new byte[512];

        while (jis.available() > 0) {
            int p = jis.read(buf);
            if (p > 0) {
                pos.write(buf, 0, p);
            }
        }
        ByteArrayInputStream pis = new ByteArrayInputStream(pos.toByteArray());
        return pis;
    }

    private static void hit(JarEntry entry) {
        addResult(entry, new ArrayList<>(stack));
    }

    private static void showEntry(JarEntry entry) {
        if (entry.getSize() > 0) {
            System.err.println(String.format("%s %s %s %s", space(stack.size()), entry.getName(), entry.getSize(), entry.getCrc()));
        }
    }

    private static void copy(JarEntry entry, String path, JarInputStream jis, JarOutputStream jos) throws IOException {

        BufferedInputStream bis = new BufferedInputStream(jis, MAX_BUFFER);

        if (entry.getSize() > MAX_BUFFER) {
            System.out.println(String.format("can't write %1$s to %2$s, size is less than MAX_BUFFER, %3$d, %4$d", entry.toString(), path, entry.getSize(), MAX_BUFFER));
        }

        if (debug) {
            System.out.println("writing " + entry.toString());
        }
        bis.mark(MAX_BUFFER);
        entry.setLastModifiedTime(FileTime.from(Instant.now()));
        jos.putNextEntry(entry);
        jos.write(bis.readAllBytes());
        jos.flush();
        jos.closeEntry();
        bis.reset();
    }

    private static void showEntry(File f) {
        if (verbose) {
            if (!f.getAbsoluteFile().getParentFile().equals(parent)) {
                System.err.println(String.format("%s %s", space(stack.size() - 1), (parent = f.getAbsoluteFile().getParentFile()).getAbsolutePath()));
            }
            System.err.println(String.format("%s %s", space(stack.size()), f.getName()));
        }
    }

    private static void dirtyWalk(File f) throws IOException {
        File temp = File.createTempFile(f.getName() + ".", ".tmp", f.getParentFile());
        temp.deleteOnExit();
        FileOutputStream fos = new FileOutputStream(temp);
        boolean dirty = false;
        try {
            dirty = walk(fos, new FileInputStream(f), f.getAbsolutePath());

            fos.flush();
            fos.close();
        } catch (Exception ex) {
            System.err.println("err: " + ex.getMessage() + " " + f.getAbsolutePath());
            if (verbose) {
                ex.printStackTrace();
            }
        } finally {
            if (dirty) {
                if (verbose) {
                    System.err.println(String.format("writing %1$s into %2$s", temp.getName(), f.getAbsolutePath()));
                }

                FileOutputStream nfos = new FileOutputStream(f);
                nfos.write(new FileInputStream(temp).readAllBytes());
                nfos.flush();
                nfos.close();
            }
        }
    }

    public static Map<String, Object> translateBindings(Map<String, List<List<String>>> source) {
        //System.out.println("source:" + source.toString());
        Map<String, Set<String>> results = new LinkedHashMap<>();

        //Set<String> paths = new HashSet<>();
        source.entrySet().stream().forEachOrdered(e -> {

            e.getValue().stream().map(v -> {
                Collections.reverse(v);
                return v.toString();
            }).forEach(key -> {
                //System.out.println(key + " " + e.getKey());
                results.computeIfAbsent(key, v -> new HashSet<>());
                results.get(key).add(e.getKey());
            });
        });

        //System.out.println("results:" + results.toString());
        return new LinkedHashMap<>(results);
    }
}

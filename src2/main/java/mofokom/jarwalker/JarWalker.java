package mofokom.jarwalker;

import au.com.devnull.graalson.GraalsonProvider;
import au.com.devnull.graalson.JsonObjectBindings;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
import java.util.Scanner;
import java.util.Set;
import java.util.Stack;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import static java.util.stream.Collectors.toList;
import java.util.zip.ZipException;
import javax.json.spi.JsonProvider;

/**
 * Hello world!
 *
 */
public class JarWalker {

    private static final String ARCHIVE = ".*\\..ar$";
    private static Deque<Path> stack = new ArrayDeque<Path>();
    private static Deque<File> jars = new ArrayDeque<File>();
    static boolean recursive = false;
    static List<String> match = new ArrayList<String>();
    static List<String> excludes = new ArrayList<String>();
    private static boolean contents;
    private static boolean duplicates;
    private static boolean detectDuplicateJars;
    private static List<String> r;
    private static Map<String, List<List<String>>> results;
    private static Map<String, File> contentFile;
    private static File parent;
    private static boolean trace;
    private static boolean debug;
    private static boolean verbose;
    private static boolean group = true;
    private static Map<JarEntry, File> entries = new HashMap<>();
    private static Map<JarEntry, Long> checkSum = new HashMap<>();
    private static boolean readOnly = true;
    private static boolean delete = false;
    private static int MAX_BUFFER = Integer.valueOf(System.getProperty("maxBuffer", "10485764"));

    private static Map config = new HashMap<>();

    private static void usage() {
        System.err.println("Usage -j [-m regexp] [-c] [-d] [-o] directory|jar|ear|war ...");
        System.err.println("-j detect duplicate jars files");
        System.err.println("-r recursive for directories");
        System.err.println("-f flat output. don't group by jar file");
        System.err.println("-m regexp to match");
        System.err.println("-c show contents of jar files");
        //System.err.println("-d show duplicates or exit after first");
        System.err.println("-d delete matching entries from jars (readOnly) -d -d (overwrite)");
        System.err.println("-x exclude regex");
        System.err.println("-v verbose, -v -v even more verbose");
        System.err.println("-h help");
        System.exit(1);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        List<File> l = new ArrayList<File>();

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
                case "-v":
                    if (debug == true) {
                        trace = true;
                    }
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
                    if (delete) {
                        readOnly = false;
                    }
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
            System.err.println(String.format("looking for %s in %s. readOnly: %s", match.toString(), l.toString(), readOnly));
        }

        config.put("spaces", Integer.valueOf(4));
        ((GraalsonProvider) JsonProvider.provider()).getConfigInUse().putAll(config);

        results = new LinkedHashMap<>();
        contentFile = new HashMap<>();

        JarWalker.walk(l);
        printResults();
    }

    private static void walk(List<File> list) throws IOException, InterruptedException {
        if (debug) {
            System.err.println("depth " + stack.size());
        }
        for (File f : list) {

            stack.push(Path.of(f.getAbsolutePath()).normalize());

            if (excludes(f.getName())) {

            } else if (f.isDirectory()) {
                walk(f);
            } else if (f.getName().matches(ARCHIVE)) {
                if (!f.exists()) {
                    System.err.println("skipping missing file " + f.getPath() + " " + f.getAbsolutePath());
                } else {
                    showEntry(f);

                    try {
                        if (delete) {
                            dirtyWalk(f);
                        } else {
                            walk(null, new FileInputStream(f), f.getAbsolutePath());
                        }
                    } catch (Exception x) {
                        System.err.println("err ." + x.getMessage() + " " + f.getAbsolutePath());
                        if (verbose) {
                            x.printStackTrace();
                        }
                    }
                }
            }
            stack.pop();
        }
    }

    private static void dirtyWalk(File f) throws IOException {

        File temp = createTempFile(f);
        FileOutputStream fos = new FileOutputStream(temp);

        boolean dirty = false;
        boolean overwrite = false;

        try {
            dirty = walk(fos, new FileInputStream(f), f.getAbsolutePath());
            fos.flush();
            fos.close();
            overwrite = dirty;
        } catch (Exception ex) {
            System.err.println("err: " + ex.getMessage() + " " + f.getAbsolutePath());

            if (verbose) {
                ex.printStackTrace();
            }
            overwrite = false;
        } finally {
            if (overwrite) {

                if (readOnly) {
                    if (verbose) {
                        System.err.println(String.format("not writing %1$s into %2$s. Use -d -d to overwrite", temp.getName(), f.getAbsolutePath()));
                    }
                } else {
                    if (verbose) {
                        System.err.println(String.format("writing %1$s into %2$s", temp.getName(), f.getAbsolutePath()));
                    }
                    copy(temp, f);
                }
            }
        }
    }

    private static boolean walk(OutputStream os, InputStream is, String path) throws IOException, InterruptedException {

        final BufferedInputStream bis = new BufferedInputStream(is, MAX_BUFFER);
        final JarInputStream jis = new JarInputStream(is);
        JarOutputStream jos = null;
        if (delete) {
            jos = create(jis, os);
        }
        try {
            return walk(jos, jis, path);
        } finally {
            if (delete) {
                jos.flush();
                jos.close();
            }
        }
    }

    private static boolean walk(JarOutputStream jos, JarInputStream jis, String path) throws IOException, InterruptedException {

        boolean dirty = false;
        JarEntry entry = null;
        try {
            while ((entry = jis.getNextJarEntry()) != null) {
                if (trace) {
                    System.err.println("processing " + entry.toString());
                }

                boolean dirtyEntry = false;

                try {

                    checkSum.put(entry, entry.getCrc());

                    if (excludes(entry.getName())) {
                        continue;
                    }

                    if (trace) {
                        showEntry(entry);
                    }

                    if (matches(entry.getRealName())) {
                        hit(entry, jis);

                        if (delete) {

                            if (verbose) {
                                System.err.println(String.format("deleting %1$s in %2$s", entry.getName(), path));
                            }
                            dirtyEntry = true;
                        }
                    }

                    if (entry.getName().matches(ARCHIVE)) {
                        stack.push(Path.of(entry.getRealName()).normalize());

                        if (verbose) {
                            showEntry(entry);
                        }

                        JarOutputStream jos2 = null;
                        JarInputStream jis2 = new JarInputStream(jis);
                        try {
                            if (delete) {
                                File f = JarWalker.createTempFile(entry);
                                jos2 = create(jis2, new FileOutputStream(f));
                            }

                            dirtyEntry = walk(jos2, jis2, entry.getName());
                            if (debug) {
                                System.err.println(entry.getName() + " " + jars.peek() + " dirty:" + dirtyEntry);
                            }
                        } finally {
                            stack.pop();
                            if (delete) {
                                jos2.flush();
                                jos2.close();

                                File f = jars.pop();

                                if (debug) {
                                    System.err.println("writing " + f + " into " + entry.getRealName() + " in " + path);
                                }
                                copy(f, jos, entry);
                            }

                        }
                    } else {
                        if (!dirtyEntry) {
                            try {
                                copy(entry, path, jis, jos);
                            } catch (ZipException x) {
                                if (verbose) {
                                    System.err.println(x.getMessage());
                                }
                            }
                        }
                    }
                } finally {
                    dirty = dirty || dirtyEntry;
                }
            }
        } catch (Exception x) {
            System.err.println(x.getMessage() + " " + path + " " + entry.getRealName() + " " + stack.toString() + " "  + jars.toString());
            if (debug) {
                x.printStackTrace();
            }
        } finally {
            if (delete) {
                if (dirty) {
                    if (debug) {
                        System.err.println(path + " is dirty ");
                    }
                }
            }
        }
        return dirty;
    }

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

    private static boolean excludes(String name) {
        return !excludes.isEmpty() && matches(excludes, name);
    }

    private static boolean matches(String name) {
        return match.isEmpty() || matches(match, name);
    }

    private static boolean matches(List<String> match, String name) {

        for (String s : match) {
            if (name.matches(s)) {
                return true;
            }
        }
        return false;
    }

    private static void addResult(JarEntry entry) {
        //TODO add relative paths only
        if (verbose) {
            System.err.println("found " + entry.getName() + " " + stack.toString());
        }
        List<List<String>> jars = results.getOrDefault(entry.getName(), new ArrayList());
        List<String> collect = stackToList();
        //collect.remove(entry.getRealName());
        jars.add(collect);

        results.put(entry.getName(), jars);

    }

    private static void printResults() throws IOException {

        Map<String, Object> bindings = translateBindings(results);
        System.out.println(new JsonObjectBindings(bindings).stringify());

        contentFile.forEach((k, v) -> {
            try {
                if (v.exists()) {
                    if (!v.isDirectory()) {
                        System.out.println(k + "\n===-\n" + new Scanner(v).useDelimiter("\\Z").next() + "\n-===\n");
                    }
                }
            } catch (Exception ex) {
                System.err.println(k + " " + v.getAbsoluteFile() + "  " + ex.getMessage());
            }
        });

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

    private static void hit(JarEntry entry, JarInputStream jis) throws IOException {

        addResult(entry);

        if (contents) {
            copyContents(jis);
        }

    }

    private static void showEntry(JarEntry entry) {
        if (entry.getSize() > 0) {
            System.err.println(String.format("%s %s %s %s", space(stack.size()), entry.getName(), entry.getSize(), entry.getCrc()));
        }
    }

    private static void showEntry(File f) {
        if (verbose) {
            if (!f.getAbsoluteFile().getParentFile().equals(parent)) {
                System.err.println(String.format("%s %s", space(stack.size() - 1), (parent = f.getAbsoluteFile().getParentFile()).getAbsolutePath()));
            }
            System.err.println(String.format("%s %s", space(stack.size()), f.getName()));
        }
    }

    public static Map<String, Object> translateBindings(Map<String, List<List<String>>> source) {
        Map<String, Set<String>> results = new LinkedHashMap<>();

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

    /**
     *
     * @param entry - the current entry
     * @param path - the path of the current jar file holding the entry
     * @param jis - the source of the current entry
     * @param jos - the destination of the entry
     * @throws IOException
     */
    private static void copy(JarEntry entry, String path, JarInputStream jis, JarOutputStream jos) throws IOException {

        if (debug) {
            System.err.println(entry.getName() + " " + path + " " + entry.getSize() + " " + entry.getCompressedSize());
        }

        if (jos == null) {
            return;
        }

        int bufSize = (int) (entry.getSize() * 1.2);
        BufferedInputStream bis = new BufferedInputStream(jis, bufSize <= 0 ? MAX_BUFFER : bufSize);

        if (debug) {
            System.err.println("writing " + entry.toString());
        }

        bis.mark(bufSize);
        entry.setLastModifiedTime(FileTime.from(Instant.now()));
        jos.putNextEntry(entry);
        byte[] buf = bis.readAllBytes();
        if (entry.getSize() != buf.length) {
            System.err.println("1." + entry.getName() + " wrong");
        }
        jos.write(buf);
        jos.closeEntry();
        jos.flush();
        bis.reset();
    }

    private static void copy(File temp, JarOutputStream jos, JarEntry entry) throws FileNotFoundException, IOException {
        if (verbose) {
            System.err.println(String.format("writing %1$s to %2$s %3$s", temp.getAbsolutePath(), entry.getName(), temp.length()));
        }
        FileInputStream fis = new FileInputStream(temp);
        byte[] buf = fis.readAllBytes();
        entry.setSize(buf.length);
        jos.putNextEntry(entry);
        jos.write(buf);
        jos.closeEntry();
        jos.flush();
    }

    private static void copy(JarInputStream jis, JarOutputStream jos, JarEntry newEntry) throws IOException {
        jos.putNextEntry(newEntry);
        byte[] buf = jis.readAllBytes();
        if (newEntry.getSize() != buf.length) {
            System.err.println("2." + newEntry.getName() + " wrong");
        }
        jos.write(buf);
        jos.closeEntry();
    }

    private static void copy(File temp, File f) throws IOException {
        FileOutputStream nfos = new FileOutputStream(f);
        nfos.write(new FileInputStream(temp).readAllBytes());
        nfos.flush();
        nfos.close();
    }

    private static JarOutputStream create(JarInputStream jis, OutputStream os) throws IOException {
        if (!delete) {
            return null;
        }
        final Manifest manifest = jis.getManifest();
        if (manifest == null) {
            return new JarOutputStream(os);
        } else {
            return new JarOutputStream(os, manifest);
        }
    }

    private static void copyContents(JarInputStream jis) throws IOException {
        File temp = File.createTempFile(stack.peek().getFileName() + ".", ".tmp");

        if (!debug) {
            temp.deleteOnExit();
        }

        FileOutputStream fos = new FileOutputStream(temp);
        byte[] buf = jis.readAllBytes();

        fos.write(buf);
        fos.close();

        contentFile.put(stackToList().toString(), temp);
    }

    private static List<String> stackToList() {

        List<Path> paths = new ArrayList<>(stack);
        Collections.reverse(paths);

        Stack<Path> list = paths.stream().reduce(new Stack<Path>(),
                (pathList, next) -> {
                    if (pathList.isEmpty()) {
                        pathList.add(next);
                    } else {
                        Path previous = pathList.peek();
                        if (next.startsWith(previous)) {
                            pathList.pop();
                        }
                        pathList.push(next);
                    }
                    return pathList;
                },
                (a, b) -> {
                    return a;
                });

        Collections.reverse(list);
        List<String> collect = list.stream().map(f -> f.toString()).collect(toList());

        return collect;
    }

    private static File createTempFile(JarEntry entry) throws IOException {
        return createTempFile(entry.getName());
    }

    private static File createTempFile(File f) throws IOException {
        return createTempFile(f.getName());
    }

    private static File createTempFile(String name) throws IOException {
        File temp = File.createTempFile(name + ".", ".tmp");
        jars.push(temp);
        if (debug) {
            System.err.println("pushed " + temp.getAbsolutePath());
        }
        if (!debug) {
            temp.deleteOnExit();
        }
        return temp;
    }
}

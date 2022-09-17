package mofokom.jarwalker;

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
import java.nio.channels.Channel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Stack;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import static java.util.stream.Collectors.toList;
import java.util.zip.ZipException;

/**
 * Hello world!
 *
 */
public class JarWalker {

    private static final String ARCHIVE = ".*\\..ar$";
    private static final String ZIP = ".*\\.(zip|gz)$";
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
    private static boolean update = false;
    private static int MAX_BUFFER = Integer.valueOf(System.getProperty("maxBuffer", "10485764"));

    private static Map config = new HashMap<>();
    private static int count;
    private static boolean showProgress;
    private static boolean zip;

    private static Channel inC;
    private static BufferedInputStream bis;
    private static int BUFFER_SIZE = 16 * 1024;
    private static boolean modify = false;

    private static void usage() {

        System.err.println("Usage java -jar jarwalker.jar -j [-m regexp] [-c] [-d] [-o] [scan directory]|jar|ear|war ... < [update file]");
        System.err.println("-j detect duplicate files in jar");
        System.err.println("-r recurse scan directories");
        System.err.println("-f flat output. don't group by jar file");
        System.err.println("-m regexp to match");
        System.err.println("-c show contents of jar files");
        //System.err.println("-d show duplicates or exit after first");
        System.err.println("-w enable write mode");
        System.err.println("-u update matching entries from standard in (readOnly) -u -u (overwrite)");
        System.err.println("-d delete matching entries from jars (readOnly) -d -d (overwrite)");
        System.err.println("-x exclude regex");
        System.err.println("-z include zip files");
        System.err.println("-v verbose, -v -v even more verbose");
        System.err.println("-h help");
        System.exit(1);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        List<File> l = new ArrayList<File>();
        inC = System.inheritedChannel();

        for (int i = 0; i < args.length; i++) {

            String s = args[i];
            switch (s) {
                case "-j":
                    detectDuplicateJars = true;
                    /*
                    } else if (s.equals("-d")) {
                    duplicates = true;
                    r = new ArrayList<String>(10000);
                     */
                    break;
                case "-p":
                    showProgress = true;
                    break;
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
                case "-z":
                    zip = true;
                    break;
                case "-x":
                    excludes.add("^.*" + args[++i] + ".*$");
                    break;
                case "-w":
                    readOnly = false;
                    break;
                case "-u":
                    if (update) {
                        readOnly = false;
                    }
                    update = true;
                    break;
                case "-m":
                    match.add("^.*" + args[++i] + ".*$");
                    break;
                default:
                    if (s.matches(ZIP)) {
                        zip = true;
                    }
                    l.add(new File(s).getAbsoluteFile());
                    break;
            }
        }

        if (l.isEmpty() && recursive) {
            l.add(new File(".").getAbsoluteFile());
        }

        modify = delete || update;
        /*
        if (inC != null && !update) {
            System.err.println("stdin detected but no update switch, override with -u");
            System.exit(1);
        } else if (inC == null && update) {
            System.err.println("update specified but no stdin detected did you < or | file");
            System.exit(1);
        } else if (inC != null && update) {
            if(!inC.isOpen()){
                System.err.println("stdin detected but was not opened");
                System.exit(1);
            }
         */
        bis = new BufferedInputStream(System.in, BUFFER_SIZE);
        //}

        if (verbose) {
            System.err.println(String.format("looking for %s in %s readOnly: %s", match.toString(), l.toString(), readOnly));
        }

        results = new LinkedHashMap<>();
        contentFile = new HashMap<>();

        if (showProgress) {
            count = count(l);
        }
        JarWalker.walk(l);
        printResults();
    }

    static int progress = 0;
    static String prevProgress;

    private static void walk(List<File> list) throws IOException, InterruptedException {
        if (debug) {
            System.err.println("depth " + stack.size());
        }

        for (File f : list) {

            try {
                stack.push(Path.of(f.getAbsolutePath()).normalize());

                if (excludes(f.getName())) {

                } else if (f.isDirectory()) {
                    walk(f);
                } else if (f.getName().matches(ARCHIVE) || (zip && f.getName().matches(ZIP))) {

                    if (showProgress) {
                        progress++;

                        String thisProgress = NumberFormat.getPercentInstance().format((double) progress / (double) count);
                        if (!thisProgress.equals(prevProgress)) {
                            System.err.println(NumberFormat.getPercentInstance().format((double) progress / (double) count) + " of " + count);
                            prevProgress = thisProgress;
                        }
                    }
                    if (!f.exists()) {
                        System.err.println("skipping missing file " + f.getPath() + " " + f.getAbsolutePath());
                    } else {
                        showEntry(f);

                        try {
                            if (update || delete) {
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
            } catch (Throwable x) {
                x.printStackTrace(System.err);
            } finally {
                stack.pop();
            }
        }
    }

    private static void dirtyWalk(File f) throws IOException {

        File temp = createTempFile(f);
        FileOutputStream fos = new FileOutputStream(temp);

        boolean dirty = false;
        boolean replace = false;

        try {
            dirty = walk(fos, new FileInputStream(f), f.getAbsolutePath());
            fos.flush();
            fos.close();
            replace = dirty;
        } catch (Exception ex) {
            System.err.println("err: " + ex.getMessage() + " " + f.getAbsolutePath());

            if (debug) {
                ex.printStackTrace();
            }
            replace = false;
        } finally {
            if (delete || update) {
                jars.pop();
                if (replace) {

                    if (readOnly) {
                        //if (verbose) {
                        System.err.println(String.format("not writing %1$s into %2$s. Use -w to overwrite", temp.getName(), f.getAbsolutePath()));
                        //}
                    } else {
                        if (verbose) {
                            System.err.println(String.format("writing %1$s into %2$s", temp.getName(), f.getAbsolutePath()));
                        }
                        copy(temp, f);
                    }
                } else {

                    System.err.println(String.format("not writing %2$s. No matches found", temp.getName(), f.getAbsolutePath()));
                }
            }
        }
    }

    private static boolean walk(OutputStream os, InputStream is, String path) throws IOException, InterruptedException {

        final JarInputStream jis = new JarInputStream(is, false);
        JarOutputStream jos = null;
        if (delete || update) {
            jos = create(jis, os);
        }
        try {
            return walk(jos, jis, path);
        } finally {
            if (delete || update) {
                jos.flush();
                jos.close();
            }
        }
    }

    private static boolean walk(JarOutputStream jos, JarInputStream jis, String path) throws IOException, InterruptedException {

        boolean dirty = false;
        JarEntry entry = null;

        if (matches("META-INF/MANIFEST.MF") && contents) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            jis.getManifest().write(baos);

            copyContents(new ByteArrayInputStream(baos.toByteArray()));
        }
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

                        if (update) {
                            if (verbose) {
                                System.err.println(String.format("updating %1$s in %2$s", entry.getName(), stackToPath()));
                            }
                            dirtyEntry = true;
                        }
                        if (delete) {

                            if (verbose) {
                                System.err.println(String.format("deleting %1$s in %2$s", entry.getName(), stackToPath()));
                            }
                            dirtyEntry = true;
                        }
                    }

                    if (entry.getName().matches(ARCHIVE)) {

                        pushEntryToStack(entry);

                        if (verbose) {
                            showEntry(entry);
                        }

                        JarOutputStream jos2 = null;
                        JarInputStream jis2 = new JarInputStream(jis);
                        try {
                            if (modify) {
                                File f = JarWalker.createTempFile(entry);
                                jos2 = create(jis2, new FileOutputStream(f));
                            }
                            dirtyEntry = walk(jos2, jis2, entry.getName());
                            if (debug) {
                                System.err.println("returned from " + entry.getName() + " in " + jars.peek() + " dirty: " + dirtyEntry);
                            }
                        } catch (Throwable x) {
                            System.err.println("err 3 " + x.getMessage() + " " + path + " " + entry.getRealName() + " " + stack.toString() + " " + jars.toString());
                            if(debug)
                                x.printStackTrace();
                        } finally {
                            stack.pop();
                            if (modify) {
                                jos2.flush();
                                jos2.close();
                                //jis2.closeEntry();
                                //jis2.close();

                                File f = jars.pop();

                                if (debug) {
                                    System.err.println("writing " + f + " into " + entry.getRealName() + " in " + path);
                                }
                                copy(f, jos, entry);
                            }

                        }
                        continue;
                    }
                    if (dirtyEntry && update) {
                        try {
                            if (bis.available() > 0) {
                                bis.mark(BUFFER_SIZE);
                                copy(entry, bis, path, jis, jos);
                            } else {
                                System.err.println("writing " + entry.getRealName() + " in " + path + " but no stdin available <");
                                System.exit(2);
                            }
                        } catch (ZipException x) {
                            if (verbose) {
                                System.err.println(x.getMessage());
                            }
                            throw x;
                        } finally {
                            bis.reset();
                        }
                    } else if (dirtyEntry && delete) {

                    } else {
                        try {
                            copy(entry, path, jis, jos);
                        } catch (ZipException x) {
                            if (verbose) {
                                System.err.println(x.getMessage());
                            }
                            throw x;
                        }
                    }
                } finally {
                    dirty = dirty || dirtyEntry;
                }
            }
        } catch (Exception x) {
            System.err.println(x.getMessage() + " jar: " + path + " entry: " + entry.getRealName() + " path: " + stackToPath() + " temp jars:" + jars.toString() + " size: " + sizeOf(path));
            if (debug) {
                x.printStackTrace();
            }
        } finally {
            if (update || delete) {
                //jars.pop();
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
            System.err.println("found " + entry.getName() + " " + stackToPath());
        }
        List<List<String>> jars = results.getOrDefault(entry.getName(), new ArrayList());
        List<String> collect = stackToList();
        //collect.remove(entry.getRealName());
        jars.add(collect);

        results.put(entry.getName(), jars);

    }

    /**
    private static void printJson(Map<String, Object> results) {
        JsonObjectBuilder ob = JsonProvider.provider().createObjectBuilder();
        results.forEach((k, v) -> {

            JsonArrayBuilder ab = JsonProvider.provider().createArrayBuilder();
            ((Set<String>) v).forEach(ab::add);
            ob.add(k, ab.build());
        });

        config.put("spaces", Integer.valueOf(4));
        JsonWriter writer = JsonProvider.provider().createWriter(System.out);
        writer.writeObject(ob.build());
    }
     */
    private static void printResults() throws IOException {

        Map<String, Object> groupedResults = translateBindings(results);

        System.out.println("{");

        groupedResults.forEach((k, v) -> {
            System.out.print(k + ":");
            System.out.println("[");
            ((Collection<String>) v).forEach(v2 -> {
                System.out.println("    " + v2);
            });
            System.out.println("]");
        });
        System.out.println("}");
        //printJson(groupedResults);

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
            pushEntryToStack(entry);
            copyContents(jis);
            stack.pop();
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
        Map<String, Collection<String>> results = new LinkedHashMap<>();

        source.entrySet().stream().forEachOrdered(e -> {

            e.getValue().stream().map(v -> {
                Collections.reverse(v);
                return toPath(v);
            }).sorted().forEach(key -> {
                results.computeIfAbsent(key, v -> new HashSet<>());
                results.get(key).add(e.getKey());
            });
        });

        results.entrySet().stream().forEachOrdered(e -> {
            ArrayList list = new ArrayList(e.getValue());
            Collections.sort(list);
            e.setValue(list);
        });

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

        int bufSize = (int) Math.max(Math.max(sizeOf(path), entry.getSize()), MAX_BUFFER);
        BufferedInputStream bis = new BufferedInputStream(jis, bufSize);

        if (trace) {
            System.err.println("copy " + entry.toString());
        }

        bis.mark(bufSize);
        copy(entry, bis, path, jis, jos);

        try {
            bis.reset();
        } catch (Exception x) {
            System.err.println(x.getMessage() + " jar: " + path + " entry: " + entry.getRealName() + " path: " + stackToPath() + " temp jars:" + jars.toString() + " size: " + sizeOf(path) + " " + bufSize);
        }
    }

    private static void copy(JarEntry entry, InputStream in, String path, JarInputStream jis, JarOutputStream jos) throws IOException {
        entry.setTime(FileTime.from(Instant.now()).toMillis());

        byte[] buf = in.readAllBytes();
        if (entry.getSize() != buf.length) {
            System.err.println("1." + entry.getName() + " wrong -- adjusting to " + buf.length);
            entry.setSize(buf.length);
        }
        jos.putNextEntry(entry);
        jos.write(buf);
        jos.closeEntry();
        jos.flush();
    }

    /**
    copy a file into a jar using the entry as a template

    @param temp
    @param jos 
    @param entry
    @throws FileNotFoundException
    @throws IOException 
     */
    private static void copy(File temp, JarOutputStream jos, JarEntry entry) throws FileNotFoundException, IOException {
        if (verbose) {
            System.err.println(String.format("copying %1$s to %2$s in %3$s size %4$s", temp.getAbsolutePath(), entry.getName(), stackToPath(), temp.length()));
        }
        FileInputStream fis = new FileInputStream(temp);
        byte[] buf = fis.readAllBytes();
        JarEntry newEntry = new JarEntry(entry);
        newEntry.setSize(buf.length);
        newEntry.setCompressedSize(-1);
        jos.putNextEntry(newEntry);
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
        if (!delete && !update) {
            return null;
        }
        final Manifest manifest = jis.getManifest();
        if (manifest == null) {
            return new JarOutputStream(os);
        } else {
            return new JarOutputStream(os, manifest);
        }
    }

    private static void copyContents(InputStream jis) throws IOException {
        File temp = debug ? new File("./tmp", stack.peek().getFileName().toString()) : File.createTempFile(stack.peek().getFileName() + ".", ".tmp");

        if (!debug) {
            temp.deleteOnExit();
        } else {
            System.err.println("created " + temp.getAbsolutePath());
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

        File temp = debug ? new File("./tmp", name) : File.createTempFile(name + ".", ".tmp");
        temp.getParentFile().mkdirs();
        jars.push(temp);
        if (debug) {
            System.err.println("pushed temp file " + temp.getAbsolutePath());
        } else {
            temp.deleteOnExit();
        }
        return temp;
    }

    private static String toPath(List<String> v) {
        Path p = null;

        for (int i = 0; i < v.size(); i++) {
            String s = v.get(i);
            if (s.matches(ARCHIVE) && i < v.size() - 1) {
                v.set(i, s + "!");
            }
        }

        if (v.size() > 1) {
            p = Paths.get(v.iterator().next(), v.subList(1, v.size()).toArray(new String[v.size() - 1]));
        } else {
            p = Paths.get(v.iterator().next());
        }

        return p.toString();
    }

    private static long sizeOf(String path) {
        File f = new File(path);
        if (f.exists()) {
            return f.length();
        } else {
            return 0;
        }
    }

    private static String stackToPath() {
        List<String> stack = stackToList();
        Collections.reverse(stack);
        return toPath(stack);
    }

    private static int count(File f) {
        int count = 0;
        if (f.isDirectory()) {
            count += count(Arrays.asList(f.listFiles()));
        } else if (f.getName().matches(ARCHIVE)) {
            count++;
        }
        return count;
    }

    private static int count(List<File> list) {
        int count = 0;
        for (File f : list) {
            count += count(f);
        }
        return count;
    }

    private static void pushEntryToStack(JarEntry entry) {
        stack.push(Path.of(entry.getRealName()).normalize());
    }

}

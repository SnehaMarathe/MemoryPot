package org.gradle.wrapper;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Minimal, self-contained Gradle wrapper.
 *
 * This replaces a broken gradle-wrapper.jar in the repo so CI can run ./gradlew.
 * It supports the common Linux/macOS path: download Gradle distribution zip from
 * distributionUrl, unzip into GRADLE_USER_HOME (or ~/.gradle), then exec gradle.
 */
public final class GradleWrapperMain {

    public static void main(String[] args) throws Exception {
        Path projectDir = Paths.get(System.getProperty("user.dir"));
        Path propsPath = projectDir.resolve("gradle").resolve("wrapper").resolve("gradle-wrapper.properties");

        Properties p = new Properties();
        try (InputStream in = new FileInputStream(propsPath.toFile())) {
            p.load(in);
        }

        String distUrl = require(p, "distributionUrl");
        String base = p.getProperty("distributionBase", "GRADLE_USER_HOME").trim();
        String path = p.getProperty("distributionPath", "wrapper/dists").trim();

        Path gradleUserHome = resolveGradleUserHome(base);
        Path distsDir = gradleUserHome.resolve(path);
        Files.createDirectories(distsDir);

        // Directory layout roughly compatible with the official wrapper.
        String urlHash = Integer.toHexString(distUrl.hashCode());
        String distFileName = distUrl.substring(distUrl.lastIndexOf('/') + 1);
        if (distFileName.isEmpty()) distFileName = "gradle-dist.zip";

        // In official wrapper, the root directory is derived from distribution name (without .zip)
        String distRootName = distFileName;
        if (distRootName.endsWith(".zip")) {
            distRootName = distRootName.substring(0, distRootName.length() - 4);
        }

        Path distRootDir = distsDir.resolve(distRootName).resolve(urlHash);
        Files.createDirectories(distRootDir);

        Path zipPath = distRootDir.resolve(distFileName);
        Path unpackDir = distRootDir.resolve("unpacked");

        if (!Files.exists(zipPath)) {
            download(distUrl, zipPath);
        }

        Path gradleHome = findGradleHome(unpackDir);
        if (gradleHome == null) {
            // Unpack if missing
            deleteQuietly(unpackDir);
            Files.createDirectories(unpackDir);
            unzip(zipPath, unpackDir);
            gradleHome = findGradleHome(unpackDir);
        }

        if (gradleHome == null) {
            System.err.println("Failed to locate Gradle home after unzip.");
            System.exit(1);
            return;
        }

        Path gradleBin = gradleHome.resolve("bin").resolve(isWindows() ? "gradle.bat" : "gradle");
        if (!Files.exists(gradleBin)) {
            System.err.println("Gradle executable not found: " + gradleBin);
            System.exit(1);
            return;
        }

        if (!isWindows()) {
            // Ensure executable bit
            gradleBin.toFile().setExecutable(true);
        }

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(buildCommand(gradleBin, args));
        pb.directory(projectDir.toFile());
        pb.inheritIO();

        Process proc = pb.start();
        int code = proc.waitFor();
        System.exit(code);
    }

    private static String require(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalStateException("Missing required property: " + key);
        }
        return v.trim();
    }

    private static Path resolveGradleUserHome(String base) {
        if ("PROJECT".equalsIgnoreCase(base)) {
            return Paths.get(System.getProperty("user.dir"));
        }
        // Default: GRADLE_USER_HOME
        String env = System.getenv("GRADLE_USER_HOME");
        if (env != null && !env.trim().isEmpty()) {
            return Paths.get(env.trim());
        }
        return Paths.get(System.getProperty("user.home"), ".gradle");
    }

    private static void download(String url, Path target) throws IOException {
        System.out.println("Downloading Gradle distribution: " + url);
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try (InputStream in = new BufferedInputStream(new URL(url).openStream())) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void unzip(Path zipFile, Path outDir) throws IOException {
        System.out.println("Unzipping: " + zipFile);
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path outPath = outDir.resolve(entry.getName()).normalize();
                if (!outPath.startsWith(outDir)) {
                    // Zip slip guard
                    throw new IOException("Bad zip entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    Files.createDirectories(outPath.getParent());
                    try (FileOutputStream fos = new FileOutputStream(outPath.toFile())) {
                        byte[] buf = new byte[8192];
                        int r;
                        while ((r = zis.read(buf)) > 0) {
                            fos.write(buf, 0, r);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static Path findGradleHome(Path unpackDir) {
        if (!Files.exists(unpackDir) || !Files.isDirectory(unpackDir)) return null;

        // Typical layout: unpackDir/gradle-8.x/
        File[] children = unpackDir.toFile().listFiles();
        if (children == null) return null;

        return Arrays.stream(children)
                .filter(File::isDirectory)
                .map(File::toPath)
                .filter(p -> Files.exists(p.resolve("bin").resolve(isWindows() ? "gradle.bat" : "gradle")))
                .findFirst()
                .orElse(null);
    }

    private static void deleteQuietly(Path path) {
        try {
            if (!Files.exists(path)) return;
            Files.walk(path)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {
        }
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }

    private static java.util.List<String> buildCommand(Path gradleBin, String[] args) {
        java.util.ArrayList<String> cmd = new java.util.ArrayList<>();
        cmd.add(gradleBin.toAbsolutePath().toString());
        cmd.addAll(Arrays.asList(args));
        return cmd;
    }
}

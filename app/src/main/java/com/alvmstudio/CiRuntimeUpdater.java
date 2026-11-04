package com.alvmstudio;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

/** Downloads only CI Runtime packages published in this repository's GitHub Releases. */
public final class CiRuntimeUpdater {
    private static final String RELEASES_API =
            "https://api.github.com/repos/akaswising-ui/ALVM/releases?per_page=100";
    private static final String RELEASE_PREFIX = "ci-runtime-v";
    private static final Pattern VERSION = Pattern.compile("^[0-9]+\\.[0-9]+\\.[0-9]+$");
    private static final int MAX_PACKAGE_BYTES = 4 * 1024 * 1024;

    public static final class Release {
        public final String version;
        public final String assetName;
        public final String downloadUrl;
        public final String assetDigest;

        Release(String version, String assetName, String downloadUrl, String assetDigest) {
            this.version = version;
            this.assetName = assetName;
            this.downloadUrl = downloadUrl;
            this.assetDigest = assetDigest;
        }
    }

    private CiRuntimeUpdater() { }

    public static Release findLatest() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(RELEASES_API).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        connection.setRequestProperty("User-Agent", "ALVM-Studio-Runtime-Updater/0.5.3");
        try {
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("GitHub returned HTTP " + code);
            }
            JSONArray releases = new JSONArray(readText(connection.getInputStream()));
            Release latest = null;
            for (int i = 0; i < releases.length(); i++) {
                JSONObject release = releases.optJSONObject(i);
                if (release == null || release.optBoolean("draft", false)) continue;
                String tag = release.optString("tag_name", "");
                if (!tag.startsWith(RELEASE_PREFIX)) continue;
                String version = tag.substring(RELEASE_PREFIX.length());
                if (!VERSION.matcher(version).matches()) continue;

                JSONArray assets = release.optJSONArray("assets");
                if (assets == null) continue;
                for (int a = 0; a < assets.length(); a++) {
                    JSONObject asset = assets.optJSONObject(a);
                    if (asset == null) continue;
                    String name = asset.optString("name", "");
                    String url = asset.optString("browser_download_url", "");
                    if (!name.endsWith(".ciruntime") || url.isEmpty()) continue;
                    Release candidate = new Release(version, name, url,
                            asset.optString("digest", ""));
                    if (latest == null || compareVersions(candidate.version, latest.version) > 0) {
                        latest = candidate;
                    }
                    break;
                }
            }
            return latest;
        } finally {
            connection.disconnect();
        }
    }

    public static int compareVersions(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        for (int i = 0; i < 3; i++) {
            int x = Integer.parseInt(a[i]);
            int y = Integer.parseInt(b[i]);
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    public static File download(Release release, File destination) throws Exception {
        if (release == null) throw new IllegalArgumentException("No CI Runtime release selected");
        URL source = new URL(release.downloadUrl);
        if (!"https".equalsIgnoreCase(source.getProtocol())
                || !"github.com".equalsIgnoreCase(source.getHost())
                || !source.getPath().startsWith("/akaswising-ui/ALVM/releases/download/")) {
            throw new SecurityException("Runtime download URL is not an official ALVM release asset");
        }

        HttpURLConnection connection = (HttpURLConnection) source.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "ALVM-Studio-Runtime-Updater/0.5.3");

        File temporary = new File(destination.getAbsolutePath() + ".part");
        if (temporary.exists()) temporary.delete();
        try {
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("Runtime download failed: HTTP " + code);
            }
            URL finalUrl = connection.getURL();
            String host = finalUrl.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(finalUrl.getProtocol())
                    || !(host.equals("github.com")
                    || host.equals("release-assets.githubusercontent.com")
                    || host.equals("objects.githubusercontent.com"))) {
                throw new SecurityException("Runtime download redirected to an untrusted host");
            }
            long announcedLength = connection.getContentLengthLong();
            if (announcedLength > MAX_PACKAGE_BYTES) {
                throw new IllegalStateException("Runtime package exceeds 4 MiB");
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0;
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[16384];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_PACKAGE_BYTES) {
                        throw new IllegalStateException("Runtime package exceeds 4 MiB");
                    }
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            }
            if (total == 0) throw new IllegalStateException("Runtime package was empty");

            String expected = release.assetDigest == null ? "" : release.assetDigest.trim();
            if (expected.startsWith("sha256:")) expected = expected.substring(7);
            if (!expected.isEmpty()) {
                StringBuilder actual = new StringBuilder();
                for (byte value : digest.digest()) actual.append(String.format(Locale.ROOT, "%02x", value));
                if (!actual.toString().equalsIgnoreCase(expected)) {
                    throw new SecurityException("GitHub asset SHA-256 verification failed");
                }
            }

            if (destination.exists() && !destination.delete()) {
                throw new IllegalStateException("Cannot replace the previous runtime download");
            }
            if (!temporary.renameTo(destination)) {
                throw new IllegalStateException("Cannot finalize the runtime download");
            }
            return destination;
        } catch (Exception error) {
            temporary.delete();
            throw error;
        } finally {
            connection.disconnect();
        }
    }

    private static String readText(InputStream input) throws Exception {
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = source.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString("UTF-8");
        }
    }
}

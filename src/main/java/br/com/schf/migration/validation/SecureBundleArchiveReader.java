package br.com.schf.migration.validation;

import br.com.schf.migration.domain.BundlePaths;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.stereotype.Component;

@Component
public class SecureBundleArchiveReader {
    private static final Set<String> ALLOWED_FILES = Set.of(
        BundlePaths.MANIFEST, BundlePaths.CHECKSUMS, BundlePaths.ORGANIZATIONS,
        BundlePaths.USERS, BundlePaths.SUPPLIERS, BundlePaths.CATEGORIES,
        BundlePaths.ACCOUNTS, BundlePaths.PAYABLES, BundlePaths.PAYMENTS, BundlePaths.SUMMARY);

    private final MigrationProperties properties;

    public SecureBundleArchiveReader(MigrationProperties properties) {
        this.properties = properties;
    }

    public ArchiveContent read(byte[] archive) {
        if (archive.length == 0 || archive.length > properties.getMaximumArchiveBytes()) {
            throw issue("ARCHIVE_SIZE_INVALID", null, "Archive size is outside the allowed limit");
        }
        var files = new LinkedHashMap<String, byte[]>();
        long totalBytes = 0;
        try (var input = new ZipArchiveInputStream(new ByteArrayInputStream(archive))) {
            var entry = input.getNextEntry();
            while (entry != null) {
                var name = normalize(entry.getName());
                if (entry.isUnixSymlink()) {
                    throw issue("SYMLINK_REJECTED", name, "Symbolic links are not allowed");
                }
                if (!entry.isDirectory()) {
                    if (!ALLOWED_FILES.contains(name)) {
                        throw issue("UNEXPECTED_FILE", name, "Archive contains an unexpected file");
                    }
                    if (files.size() >= properties.getMaximumFiles()) {
                        throw issue("FILE_LIMIT_EXCEEDED", name, "Archive contains too many files");
                    }
                    if (files.containsKey(name)) {
                        throw issue("DUPLICATE_FILE", name, "Archive contains duplicate file names");
                    }
                    var content = readEntry(input, name);
                    totalBytes += content.length;
                    if (totalBytes > properties.getMaximumUncompressedBytes()) {
                        throw issue("UNCOMPRESSED_LIMIT_EXCEEDED", name,
                            "Archive exceeds the uncompressed size limit");
                    }
                    files.put(name, content);
                }
                entry = input.getNextEntry();
            }
        } catch (BundleValidationException ex) {
            throw ex;
        } catch (IOException ex) {
            throw issue("INVALID_ZIP", null, "Archive cannot be read as a ZIP bundle");
        }
        return new ArchiveContent(sha256(archive), Map.copyOf(files));
    }

    private byte[] readEntry(ZipArchiveInputStream input, String name) throws IOException {
        var output = new ByteArrayOutputStream();
        var buffer = new byte[8192];
        long count = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            count += read;
            if (count > properties.getMaximumEntryBytes()) {
                throw issue("ENTRY_LIMIT_EXCEEDED", name, "Archive entry exceeds the size limit");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private String normalize(String name) {
        if (name == null || name.isBlank() || name.startsWith("/") || name.startsWith("\\")
            || name.contains("\\") || name.contains(":") || name.contains("\0")) {
            throw issue("UNSAFE_PATH", null, "Archive entry path is unsafe");
        }
        var normalized = java.nio.file.Path.of(name).normalize().toString().replace('\\', '/');
        if (!normalized.equals(name) || normalized.startsWith("../") || normalized.contains("/../")) {
            throw issue("ZIP_SLIP_REJECTED", null, "Archive entry path is unsafe");
        }
        return normalized;
    }

    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private BundleValidationException issue(String code, String file, String message) {
        return new BundleValidationException(java.util.List.of(new ValidationIssue(code, file, null, message)));
    }

    public record ArchiveContent(String bundleId, Map<String, byte[]> files) {
    }
}

package br.com.schf.migration.infrastructure;

import br.com.schf.migration.validation.BundleValidationException;
import br.com.schf.migration.validation.MigrationProperties;
import br.com.schf.migration.validation.ValidationIssue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class MigrationUploadWorkspace {
    private final MigrationProperties properties;

    public MigrationUploadWorkspace(MigrationProperties properties) {
        this.properties = properties;
    }

    public <T> T process(String fileName, byte[] content, Function<byte[], T> operation) {
        validateUpload(fileName, content);
        Path temporary = null;
        try {
            var root = Path.of(properties.getWorkbench()).toAbsolutePath().normalize();
            Files.createDirectories(root);
            if (Files.isSymbolicLink(root)) throw invalid("WORKBENCH_SYMLINK", "Migration workbench is unsafe");
            temporary = Files.createTempFile(root, "bundle-", ".upload");
            Files.write(temporary, content);
            return operation.apply(Files.readAllBytes(temporary));
        } catch (IOException ex) {
            throw invalid("WORKBENCH_IO_ERROR", "Migration upload could not be processed");
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
    }

    private void validateUpload(String name, byte[] content) {
        if (content == null || content.length == 0 || content.length > properties.getMaximumArchiveBytes())
            throw invalid("UPLOAD_SIZE_INVALID", "Migration upload size is invalid");
        var lower = name == null ? "" : name.toLowerCase();
        if (!(lower.endsWith(".schf") || lower.endsWith(".zip")))
            throw invalid("UPLOAD_EXTENSION_INVALID", "Migration upload extension is not allowed");
    }

    private BundleValidationException invalid(String code, String message) {
        return new BundleValidationException(List.of(new ValidationIssue(code, null, null, message)));
    }
}

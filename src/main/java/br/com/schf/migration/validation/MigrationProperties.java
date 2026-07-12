package br.com.schf.migration.validation;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "schf.migration")
public class MigrationProperties {
    @Min(1024) private long maximumArchiveBytes = 25L * 1024 * 1024;
    @Min(1024) private long maximumUncompressedBytes = 100L * 1024 * 1024;
    @Min(1024) private long maximumEntryBytes = 20L * 1024 * 1024;
    @Min(9) private int maximumFiles = 25;
    @Min(1) private int batchSize = 500;
    private String workbench = java.nio.file.Path.of(
        System.getProperty("java.io.tmpdir"), "schf-migration-workbench").toString();

    public long getMaximumArchiveBytes() { return maximumArchiveBytes; }
    public void setMaximumArchiveBytes(long value) { maximumArchiveBytes = value; }
    public long getMaximumUncompressedBytes() { return maximumUncompressedBytes; }
    public void setMaximumUncompressedBytes(long value) { maximumUncompressedBytes = value; }
    public long getMaximumEntryBytes() { return maximumEntryBytes; }
    public void setMaximumEntryBytes(long value) { maximumEntryBytes = value; }
    public int getMaximumFiles() { return maximumFiles; }
    public void setMaximumFiles(int value) { maximumFiles = value; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int value) { batchSize = value; }
    public String getWorkbench() { return workbench; }
    public void setWorkbench(String value) { workbench = value; }
}

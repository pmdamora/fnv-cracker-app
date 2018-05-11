package passwordcrack.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Defines "file" application properties such as the location of uploaded files
 */
@ConfigurationProperties(prefix = "file")
public class FileStorageProperties {
    private String uploadDir;

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }
}
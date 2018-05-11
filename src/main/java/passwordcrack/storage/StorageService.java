package passwordcrack.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;


/**
 * Defines the StorageService interface
 */
public interface StorageService {
    String store(MultipartFile file);
    Resource loadFileAsResource(String fileName);
    void init();
    void deleteAll();
}

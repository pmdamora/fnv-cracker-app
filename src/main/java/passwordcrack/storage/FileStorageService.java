package passwordcrack.storage;


import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import passwordcrack.storage.exceptions.StorageException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.FileSystemUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class FileStorageService implements StorageService {

    private final Path fileStorageLocation;


    /**
     * Constructor. Generates the absolute path of the upload directory on the local filesystem
     * @param fileStorageProperties The object storing file properties
     */
    @Autowired
    public FileStorageService(FileStorageProperties fileStorageProperties) {
        this.fileStorageLocation = Paths.get(fileStorageProperties.getUploadDir())
                .toAbsolutePath().normalize();
    }


    /**
     * Saves the uploaded file to the local filesystem
     * @param file the uploaded file
     * @return fileName
     */
    @Override
    public String store(MultipartFile file) {
        // Clean the file path
        String fileName = StringUtils.cleanPath(file.getOriginalFilename());

        try {
            if (file.isEmpty()) {
                throw new StorageException("Failed to store empty file " + fileName);
            }
            if (fileName.contains("..")) {
                // This is a security check
                throw new StorageException(
                        "Cannot store file with relative path outside current directory "
                                + fileName);
            }
            try (InputStream inputStream = file.getInputStream()) {
                // Copy to target location, replace any existing files with the same name.
                Files.copy(inputStream, this.fileStorageLocation.resolve(fileName),
                        StandardCopyOption.REPLACE_EXISTING);
            }

            return fileName;
        } catch (IOException e) {
            throw new StorageException("Failed to store file " + fileName, e);
        }
    }


    /**
     * Serves uploaded files to the user for download
     * @param fileName the name of the requested file
     * @return resource the resource
     */
    @Override
    public Resource loadFileAsResource(String fileName) {
        try {
            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if(resource.exists()) {
                return resource;
            } else {
                throw new FileNotFoundException("File not found " + fileName);
            }
        } catch (MalformedURLException | FileNotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Creates the upload directory if it doesn't already exist
     */
    @Override
    public void init() {
        try {
            Files.createDirectories(fileStorageLocation);
        }
        catch (IOException e) {
            throw new StorageException("Could not initialize storage", e);
        }
    }


    /**
     * Recursively deletes all uploaded files. Useful for testing.
     */
    @Override
    public void deleteAll() {
        FileSystemUtils.deleteRecursively(fileStorageLocation.toFile());
    }
}
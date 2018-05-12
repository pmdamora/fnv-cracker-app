package passwordcrack.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import passwordcrack.storage.FileStorageProperties;
import passwordcrack.storage.StorageService;
import passwordcrack.storage.UploadResponse;
import passwordcrack.cracking.PasswordBreaker;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;


/**
 * Resources: https://spring.io/guides/gs/uploading-files/ helped significantly in regard to file uploads.
 * https://spring.io/guides/gs/messaging-stomp-websocket/ helped significantly in regard to web sockets
 */
@RestController
public class FileUploadController {
    private static final String ACCEPTABLE_TYPE = "text/plain";

    private StorageService storageService;
    private SimpMessagingTemplate template;
    private final Path fileStorageLocation;


    /**
     * Constructor injects storage service field
     * @param storageService storage service interface
     * @param fileStorageProperties file storage properties
     * @param template simple messaging template
     */
    @Autowired
    public FileUploadController(StorageService storageService, FileStorageProperties fileStorageProperties,
                                SimpMessagingTemplate template) {
        this.storageService = storageService;
        this.fileStorageLocation = Paths.get(fileStorageProperties.getUploadDir())
                .toAbsolutePath().normalize();
        this.template = template;
    }


    /**
     * Creates an API for file upload.
     * @param file The multipart file received from the client
     * @return ResponseEntiry Returns a response object containing the filename, file uri
     * content type, and size.
     */
    @PostMapping("/uploadFile")
    public ResponseEntity<UploadResponse> uploadFile(@RequestParam("file") MultipartFile file) {
        // Save the file
        // String fileName = storageService.store(file);
        PasswordBreaker pb = new PasswordBreaker();

        // Check that the client provided the correct file type
        if (!file.getContentType().equals(ACCEPTABLE_TYPE)) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
        }

        String fileName = StringUtils.cleanPath(file.getOriginalFilename());

        // Crack the passwords
        CompletableFuture.runAsync(() -> {
            try {
                pb.crack(file, fileName, fileStorageLocation);
            } catch (InterruptedException | ExecutionException e) {
                throw new IllegalStateException(e);
            }
        });
        CompletableFuture.runAsync(() -> crackResults(pb));

        // Create the file uri
        String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/download/")
                .path(fileName)
                .toUriString();

        // Return file response object
        UploadResponse response = new UploadResponse(fileName, fileDownloadUri);

        return ResponseEntity.accepted().body(response);
    }


    /**
     * Creates an API for file download.
     * @param fileName The name of the file to be downloaded
     * @param request The http request
     * @return ResponseEntity the object containing the file to download
     *
     * Resources: https://www.callicoder.com/spring-boot-file-upload-download-rest-api-example/
     */
    @GetMapping("/download/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName, HttpServletRequest request) {
        // Load file as Resource
        Resource resource = storageService.loadFileAsResource(fileName);

        // Try to determine file's content type
        String contentType = null;
        try {
            contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Fallback to the default content type if type could not be determined
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }


    /**
     * Polls the server to determine when the passwords are finished cracking, and sends them to the client
     * as text content.
     * @param pb the password breaker instance
     */
    private void crackResults(PasswordBreaker pb) {
        String result = "";
        BlockingQueue status = pb.getResultQueue();
        String done;

        // We communicate with the PasswordBreaker thread with a blockingqueue
        // When the output has been generated, PasswordBreaker adds an item to the
        // queue, which release the block on this process, so we can move foreward.
        try {
            done = status.take().toString();
            if (done.equals("finished")) {
                result = pb.getResult();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // In case something funky happens
        if (result.equals("")) {
            result = "Error fetching file contents";
        }

        // Send the result
        template.convertAndSend("/topic/crackresults", result);
    }
}

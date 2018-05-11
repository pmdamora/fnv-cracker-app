package passwordcrack.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import passwordcrack.cracking.CrackMessage;
import passwordcrack.storage.StorageService;
import passwordcrack.storage.UploadResponse;
import passwordcrack.cracking.PasswordBreaker;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;


/**
 * Resources: https://spring.io/guides/gs/uploading-files/ helped significantly in regard to file uploads.
 * https://spring.io/guides/gs/messaging-stomp-websocket/ helped significantly in regard to web sockets
 */
@RestController
public class FileUploadController {
    private static final String ACCEPTABLE_TYPE = "text/plain";
    private StorageService storageService;
    private PasswordBreaker passwordBreaker;


    /**
     * Constructor injects storage service field
     * @param storageService storage service interface
     * @param passwordBreaker cracks uploaded hashes
     */
    @Autowired
    public FileUploadController(StorageService storageService, PasswordBreaker passwordBreaker) {
        this.storageService = storageService;
        this.passwordBreaker = passwordBreaker;
    }


    /**
     * Creates an API for file upload.
     * @param file The multipart file received from the client
     * @return UploadResponse Returns a response object containing the filename, file uri
     * content type, and size.
     */
    @PostMapping("/uploadFile")
    public ResponseEntity<UploadResponse> uploadFile(@RequestParam("file") MultipartFile file) throws InterruptedException, ExecutionException {
        // Save the file
        // String fileName = storageService.store(file);

        // Check that the client provided the correct file type
        if (!file.getContentType().equals(ACCEPTABLE_TYPE)) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
        }

        String fileName = StringUtils.cleanPath(file.getOriginalFilename());

        // Crack the passwords
        passwordBreaker.crack(file, fileName);

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
     * @return A response to the server
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
     * @param message the supplied message
     * @return an updated CrackMessage
     */
    @MessageMapping("/crack")
    @SendTo("/topic/crackstatus")
    public CrackMessage crackStatus(CrackMessage message) {
        String result = "";
        BlockingQueue status = passwordBreaker.getStatus();
        String done;

        // We communicate with the PasswordBreaker thread with a blockingqueue
        // When the output has been generated, PasswordBreaker adds an item to the
        // queue, which release the block on this process, so we can move foreward.
        try {
            done = status.take().toString();
            if (done.equals("finished")) {
                result = passwordBreaker.getResult();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // In case something funky happens
        if (result.equals("")) {
            result = "Error fetching file contents";
        }

        message.setContent(result);
        return message;
    }
}

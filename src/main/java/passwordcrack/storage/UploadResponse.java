package passwordcrack.storage;


/**
 * Defines the UploadResponse model. This class is used to create objects to send to the client
 * in response to a file upload.
 */
public class UploadResponse {
    private String fileName;
    private String fileDownloadUri;


    /**
     * Constructor
     * @param fileName The name of the file
     * @param fileDownloadUri The Uri where the file can be downloaded by the client
     */
    public UploadResponse(String fileName, String fileDownloadUri) {
        this.fileName = fileName;
        this.fileDownloadUri = fileDownloadUri;
    }


    // Getters and setters below

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileDownloadUri() {
        return fileDownloadUri;
    }

    public void setFileDownloadUri(String fileDownloadUri) {
        this.fileDownloadUri = fileDownloadUri;
    }
}
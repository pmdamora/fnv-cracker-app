package passwordcrack.storage.exceptions;


/**
 * A StorageException is called when an error occurs that prevents the file from being stored.
 */
public class StorageException extends RuntimeException {
    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
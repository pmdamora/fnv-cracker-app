package passwordcrack.cracking;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import passwordcrack.storage.FileStorageProperties;
import sun.security.provider.NativePRNG;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


@Component
public class PasswordBreaker {
    private final int CORES = Runtime.getRuntime().availableProcessors();
    // private final String MAP_LOCATION = "passwords.properties";
    private static final String COMMON_PASSWORDS_LOCATION = "data/common.txt";

    private BlockingQueue status = new ArrayBlockingQueue<>(1);
    private BlockingQueue queue = new ArrayBlockingQueue<>(100);
    private ConcurrentHashMap<String, String> keyHashes = new ConcurrentHashMap<>(); // Shared table, stores all generated values
    private Queue<String> commons = new LinkedList<>(); // A LL storing all of the common passwords
    private AtomicBoolean finished = new AtomicBoolean(); // Signals to threads whether or not we are done
    private AtomicInteger foundPasswords = new AtomicInteger();

    private final Path fileStorageLocation;
    private  StringBuilder result = new StringBuilder();


    /**
     * Constructor. Generates the absolute path of the upload directory on the local filesystem
     * @param fileStorageProperties The object storing file properties
     */
    @Autowired
    public PasswordBreaker(FileStorageProperties fileStorageProperties) {
        this.fileStorageLocation = Paths.get(fileStorageProperties.getUploadDir())
                .toAbsolutePath().normalize();
    }


    public BlockingQueue getStatus() {
        return status;
    }

    public String getResult() {
        return result.toString();
    }

    /**
     * This method cracks all of the passwords. This is done by a brute-force method, where every possible string
     * is generated until one is found to be a match for the supplied hash digest. All computed hash-pass pairs are
     * store in a ConcurrentHashMap (thread safe).
     *
     * Resources: https://www.callicoder.com/java-executor-service-and-thread-pool-tutorial/
     * http://coderscampus.com/java-multithreading-java-util-concurrent/
     * https://www.callicoder.com/java-callable-and-future-tutorial/
     */
    @Async
    public void crack(MultipartFile file, String fileName) throws InterruptedException, ExecutionException {
        // loadMap(); // Load a saved map if we can
        parseFiles(file);
        getGuesses();
        outputResults(fileName);
        // saveMap(); // Save the map we just worked so hard to make

    }


    /**
     * Outputs the results of the computation to the specified output file.
     */
    @SuppressWarnings("unchecked")
    private void outputResults(String fileName) {
        String path = String.valueOf(this.fileStorageLocation.resolve(fileName));

        try (OutputStream writer = new FileOutputStream(path)) {
            Set<String> keys = keyHashes.keySet();
            String s;

            // Add each key to the OutputStream
            for (String key : keys) {
                s = keyHashes.get(key) + " : " + key + "\n";
                result.append(s);
                writer.write(s.getBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            status.put("finished");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    /**
     * Opens the input file and reads each line into a hash table
     */
    private void parseFiles(MultipartFile file) {
        // Open the input file
        try (InputStream inputStream = file.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))){
            String line;

            // Add each supplied hash to the table
            while ((line = reader.readLine()) != null) {
                if (!keyHashes.containsKey(line))
                    keyHashes.put(line, "");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Open the common passwords file
        try (BufferedReader reader = new BufferedReader(new FileReader(COMMON_PASSWORDS_LOCATION))) {
            String line;

            // Add each password to the queue
            while ((line = reader.readLine()) != null) {
                commons.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Generate all possible passwords until all have been found
     * @throws InterruptedException occurs when the thread is interrupted
     * @throws ExecutionException occurs when the thread is interrupted
     */
    @SuppressWarnings("unchecked")
    private void getGuesses() throws InterruptedException, ExecutionException {
        Runnable producer = new StringGenerator(queue, finished, commons);
        Callable consumer = new HashChecker(queue, finished, keyHashes, foundPasswords);

        // Create the executor service with the maximum number of threads
        ExecutorService executorService = Executors.newFixedThreadPool(CORES);

        // Create consumer thread list
        List<Callable<String>> threadList = new ArrayList<>();
        for (int j = 0; j < CORES-1; j++) {
            threadList.add(consumer);
        }

        // Producer and consumer communicate using a BlockingQueue
        executorService.submit(producer);

        // Run all of the consumer threads
        List<Future<String>> future = executorService.invokeAll(threadList);

        // Get the results of the future (blocks until the execution finishes)
        future.get(0).get();

        // Stop all tasks
        executorService.shutdownNow();
    }


//    /**
//     * Load the save data into a hash map
//     */
//    private static void loadMap() {
//        Properties properties = new Properties();
//
//        // Load properties from file
//        try (FileInputStream fileOut = new FileInputStream(MAP_LOCATION)) {
//            properties.load(fileOut);
//            // Fill in the empty hash map
//            for (String key : properties.stringPropertyNames()) {
//                keyHashes.put(key, properties.get(key).toString());
//            }
//        } catch (IOException e) {
//            System.out.println("The file doesn't exist. Starting from scratch...");
//        }
//    }
//
//
//    /**
//     * Save the generated hash map of hash-password pairs to a file so it can be loaded for later use.
//     */
//    private static void saveMap() {
//        Properties properties = new Properties();
//
//        // Add each entry to the Properties object
//        for (ConcurrentHashMap.Entry<String,String> entry : keyHashes.entrySet()) {
//            properties.put(entry.getKey(), entry.getValue());
//        }
//
//        // Store the properties in a file
//        System.out.println("Saving hash map to file. This may take a while.");
//        try (FileOutputStream fileOut = new FileOutputStream(MAP_LOCATION)) {
//            properties.store(fileOut, null);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
}

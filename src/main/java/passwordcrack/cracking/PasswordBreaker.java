package passwordcrack.cracking;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


@Component
public class PasswordBreaker {
    private final int CORES = Runtime.getRuntime().availableProcessors();
    private static final String COMMON_PASSWORDS_LOCATION = "data/common.txt";

    private BlockingQueue resultQueue;
    private BlockingQueue queue;
    private ConcurrentHashMap<String, String> keyHashes; // Shared table, stores all generated values
    private Queue<String> commons; // A queue storing all of the common passwords
    private AtomicBoolean finished; // Signals to threads whether or not we are done
    private AtomicInteger foundPasswords;
    private StringBuilder result;


    /**
     * Constructor.
     */
    public PasswordBreaker() {
        this.resultQueue = new ArrayBlockingQueue<>(1);
        this.queue  = new ArrayBlockingQueue<>(100);
        this.keyHashes = new ConcurrentHashMap<>();
        this.commons = new LinkedList<>();
        this.finished = new AtomicBoolean();
        this.foundPasswords = new AtomicInteger();
        this.result = new StringBuilder();
    }

    public BlockingQueue getResultQueue() {
        return resultQueue;
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
    public void crack(MultipartFile file, String fileName, Path fileStorageLocation) throws InterruptedException, ExecutionException {
        parseFiles(file);
        getGuesses();
        outputResults(fileName, fileStorageLocation);
    }


    /**
     * Outputs the results of the computation to the specified output file.
     */
    @SuppressWarnings("unchecked")
    private void outputResults(String fileName, Path fileStorageLocation) {
        String path = String.valueOf(fileStorageLocation.resolve(fileName));

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
            resultQueue.put("finished");
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
}

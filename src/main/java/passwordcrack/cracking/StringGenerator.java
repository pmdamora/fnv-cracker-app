package passwordcrack.cracking;

import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Produces possible Strings and adds them to the BlockingQueue to wait to be consumed
 */
public class StringGenerator implements Runnable {
    private final BlockingQueue queue;
    private final AtomicBoolean finished;
    private final Queue<String> commons;
    private static final char MIN_CHAR = 'a';
    private static final char MAX_CHAR = 'z';
    private static final int MIN_LENGTH = 1;
    private static final int MAX_LENGTH = 7;


    StringGenerator(BlockingQueue queue, AtomicBoolean finished, Queue<String> commons) {
        this.queue = queue;
        this.finished = finished;
        this.commons = commons;
    }


    /**
     * Overrides the Runnable run() method.
     * Generates every possible string between MIN and MAX LENGTH
     */
    @Override
    @SuppressWarnings("unchecked")
    public void run() {
        char[] generated;
        try {
            // Check all the common passwords
            // Basically just remove all of the passwords from the queue and add to the BlockingQueue
            // TODO: This is a kinda roundabout way to do this, i.e. a second queue shouldn't be necessary
            while (!commons.isEmpty()) {
                queue.put(commons.remove());

                if (finished.get()) return;
            }

            // Now start guessing
            // Loop through each possible length
            for (int i = MIN_LENGTH; i <= MAX_LENGTH; i++) {
                // Generate all possible strings of length i
                generated = new char[i];
                generate(generated, i-1);

                if (finished.get()) return;
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }

        // Make the consumers stop
        finished.set(true);
    }


    /**
     * Generate all possible strings
     * @param password The character array for the password guesses
     * @param pos The current position in the char array
     */
    @SuppressWarnings("unchecked")
    private void generate(char[] password, int pos) throws InterruptedException {
        // This is the beginning of the string, add it to the blocking queue
        if (pos < 0) {
            String passString = String.valueOf(password); // the generated password to check
            queue.put(passString);
            return;
        }

        // Generate every possible string, recursively
        for (password[pos] = MIN_CHAR; password[pos] <= MAX_CHAR; password[pos]++) {
            generate(password, pos-1);
        }
    }
}

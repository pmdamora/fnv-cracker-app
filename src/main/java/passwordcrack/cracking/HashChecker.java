package passwordcrack.cracking;

import java.math.BigInteger;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Consumes data in the BlockingQueue until the correct string is found
 */
public class HashChecker implements Callable {
    private final BlockingQueue queue;
    private final AtomicBoolean finished;
    private final AtomicInteger foundPasswords;
    private final ConcurrentHashMap<String, String> keyHashes;
    private final BigInteger FNV_PRIME = new BigInteger("309485009821345068724781371");
    private final BigInteger FNV_INIT = new BigInteger("6c62272e07bb014262b821756295c58d", 16);
    private final BigInteger FNV_MOD = new BigInteger("2").pow(128);
    private final int TOTAL_PASSWORDS;


    HashChecker(BlockingQueue queue, AtomicBoolean finished, ConcurrentHashMap<String, String> keyHashes,
                AtomicInteger foundPasswords) {
        this.queue = queue;
        this.finished = finished;
        this.keyHashes = keyHashes;
        this.foundPasswords = foundPasswords;
        this.TOTAL_PASSWORDS = keyHashes.size();
    }

    @Override
    public String call() {
        try {
            String passString;
            String hashString;
            int found;

            // Continue until all possibilities are generated
            while (!finished.get()) {
                passString = queue.take().toString();
                hashString = fnv(passString.getBytes());

                // We've got a match
                if (keyHashes.get(hashString) != null && keyHashes.get(hashString).equals("")) {
                    keyHashes.put(hashString, passString);
                    found = foundPasswords.incrementAndGet();
                    // System.out.println("Match found: " + passString + " - " + hashString);

                    // Are we done yet?
                    if (found == TOTAL_PASSWORDS) {
                        finished.set(true);
                    }
                }

                // Interrupt
                if (Thread.currentThread().isInterrupted()){
                    return null;
                }
            }

            return null;
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Implements the FNV-1 128bit algorithm
     * https://www.wikiwand.com/en/Fowler%E2%80%93Noll%E2%80%93Vo_hash_function#/FNV-1_hash
     * @param data the byte array of the password to be hashed
     * @return the hash string
     */
    private String fnv(byte[] data) {
        BigInteger hash = FNV_INIT;

        for (byte b : data) {
            hash = hash.multiply(FNV_PRIME).mod(FNV_MOD);
            hash = hash.xor(BigInteger.valueOf((int) b & 0xff));
        }

        return hash.toString(16);
    }
}

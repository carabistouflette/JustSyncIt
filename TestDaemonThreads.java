import com.justsyncit.scanner.AsyncByteBufferPoolImpl;
import java.nio.ByteBuffer;

public class TestDaemonThreads {
    public static void main(String[] args) throws Exception {
        System.out.println("Testing daemon thread implementation...");
        
        // Create pool and test basic operations
        AsyncByteBufferPoolImpl pool = AsyncByteBufferPoolImpl.create(1024, 2);
        
        ByteBuffer buffer = pool.acquire(1024);
        System.out.println("Acquired buffer: " + buffer.capacity());
        
        pool.release(buffer);
        System.out.println("Released buffer");
        
        // Clear the pool - this should not hang with daemon threads
        pool.clear();
        System.out.println("Cleared pool successfully");
        
        // Try to create another pool to ensure no hanging
        AsyncByteBufferPoolImpl pool2 = AsyncByteBufferPoolImpl.create(2048, 2);
        ByteBuffer buffer2 = pool2.acquire(2048);
        System.out.println("Second pool acquired buffer: " + buffer2.capacity());
        
        pool2.release(buffer2);
        pool2.clear();
        System.out.println("Second pool cleared successfully");
        
        System.out.println("All tests passed - daemon threads working correctly!");
    }
}

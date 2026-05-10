import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SharedPool {

    static ExecutorService pool = Executors.newFixedThreadPool(50);
}

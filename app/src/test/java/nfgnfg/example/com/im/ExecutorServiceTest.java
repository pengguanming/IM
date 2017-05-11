package nfgnfg.example.com.im;

import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by ngfngf on 2017/5/8.
 */

public class ExecutorServiceTest {
    @Test
    public void simpleUsage() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        for (int i = 0; i < 10; i++) {
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().getName() + ":" + System.currentTimeMillis());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        Thread.sleep(4000);
    }
}

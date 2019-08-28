package com.hzeng.zerodowntimerollingupdate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@Component
/**
 * REST API controller to handle below REST API request
 * /k8s/demo        ---- A demo request, and server will return "OK" response
 * /k8s/longTask    ---- A long task request, the server will use sleep to simulate time consuming task,
 *                       and return "OK" response.
 */
public class MyController {

    private static Logger logger = LoggerFactory.getLogger(MyController.class);
    private static ExecutorService executor = Executors.newFixedThreadPool(5);
    private AtomicInteger taskId = new AtomicInteger(0);
    private static Map<Key, String> memoryLeakMap = new HashMap<Key, String>();
    private static final Collection<Object> leak = new ArrayList<>();
    private final static String OK_RESPONSE = "OK\r\n";
    private final static String OK_RESPONSE_V1 = "OK - V1\r\n";
    private final static String OK_RESPONSE_V2 = "OK - V2\r\n";

    @GetMapping("/ping")
    public String ping() {
        return "pong\r\n";
    }

    @GetMapping("/k8s/demo")
    public String demoRequest() {
        logger.info(" >>> Received demo request");

        //return v1 response to indicate v1, return v2 response to indicate v2
        return OK_RESPONSE_V1;
        //return OK_RESPONSE_V2;
    }

    @GetMapping("/k8s/longTask")
    public ResponseEntity<String> runLongTask() {
        logger.info(" >>> Received long task request");
        LongTask task = new LongTask(taskId.incrementAndGet());
        Future<Integer> result = executor.submit(task);
        Integer taskId = null;
        try {
            taskId = result.get(11, TimeUnit.SECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException ex) {
            logger.info("runLongTask >> Exception: {}.", ex.toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("\"{\"status\": \"fail - V1\"}\"\r\n");
        }
        if (taskId != null) {
            return ResponseEntity.status(HttpStatus.OK)
                    .body("\"{\"status\": \"OK - V1\"}\"\r\n");
        } else {
            logger.info("runLongTask >> Unexpected error! Empty result!");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("\"{\"status\": \"fail - V1\"}\"\r\n");
        }
    }

    /**
     * Simulate a memory leak scenario to monitor the k8s behaviour to see the pod will be re-created or not.
     *
     * @return
     */
    @GetMapping("/k8s/memoryleak")
    public String simulateMemoryLeak() {
        logger.info("simulateMemoryLeak >> Simulate the JVM memory leak.");
        MemoryLeakTask task = new MemoryLeakTask();
        executor.execute(task);
        return OK_RESPONSE;
    }

    /**
     * Simulate a SIGN TERM to see the pod will be re-created or not.
     */
    @GetMapping("/k8s/shutdown")
    public String shutdown() {
        logger.info("shutdown >> Shutdown the application...");
        executor.execute(() -> {
            logger.info("shutdown >> Call System.exit to shutdown the application.");
            System.exit(0);
        });
        return OK_RESPONSE;
    }

    /**
     * Simulate a full GC scenario to pause JVM for a while.
     *
     * @return
     */
    @GetMapping("/k8s/fullgc")
    public String simulateFullGC() {
        logger.info("simulateFullGC >> Simulate the full GC scenario.");
        FullGCTask task = new FullGCTask();
        executor.execute(task);
        return OK_RESPONSE;
    }

    static class LongTask implements Callable<Integer> {
        private int taskId;

        public LongTask(int id) {
            taskId = id;
        }

        public Integer call() {
            try {
                logger.info("LongTask >> Starting a background task, task id: {}.", taskId);
                Thread.sleep(10000); //simulate a long processing time in background
            } catch (InterruptedException e) {
                logger.info("LongTask >> Interrupted Exception, ignore it.");
                return null;
            }
            logger.info("LongTask >> Done! Task id: {} ", taskId);
            return taskId;
        }
    }

    static class MemoryLeakTask implements Runnable {
        public void run() {
            logger.info("MemoryLeakTask >> OOM test started..");
            while (true) {
                for (int i = 0; i < 1000000; i++) {
                    if (!memoryLeakMap.containsKey(new Key(i)))
                        memoryLeakMap.put(new Key(i), "Number:" + i);
                }
            }
        }
    }

    static class FullGCTask implements Runnable {
        // Run with: -XX:+PrintGCApplicationStoppedTime -XX:+PrintGCDetails
        // Notice that all the stop the world pauses coincide with GC pauses
        private volatile Object sink = null;

        public void run() {
            logger.info("FullGCTask >> Trigger a Full GC to cause JVM to pause for a while.");
            if (sink == null) {
                logger.info("FullGCTask >> sink is null. Just want to read this object to avoid PMD bug.");
            }
            while (true) {
                try {
                    leak.add(new byte[1024 * 1024]);
                    sink = new byte[1024 * 1024];
                } catch (OutOfMemoryError e) {
                    logger.info("FullGCTask >> OutOfMemoryError occurred.");
                    leak.clear();
                }
            }
        }
    }

    static class Key {
        Integer id;

        Key(Integer id) {
            this.id = id;
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            /*
            boolean response = false;
   if (o instanceof Key) {
      response = (((Key)o).id).equals(this.id);
   }
   return response;
             */
            //in-correct equals method for trigger the OOM exception.
            if (this == o) return true;
            else return false;
        }
    }

}

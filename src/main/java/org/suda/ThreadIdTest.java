package org.suda;

import java.util.Map;

public class ThreadIdTest {
    /**
     * Main Thread ID = 1
     * Thread name = Thread-0, ID = 15
     * Thread name = Thread-1, ID = 16
     * Thread name = Thread-2, ID = 17
     * @param args
     */
    public static void main(String[] args) {
        System.out.println("Main Thread ID = " + Thread.currentThread().getId());
        for (int i = 0; i < 3; i++) {
            Thread t = new Thread(() -> {
                System.out.println("Thread name = " + Thread.currentThread().getName()
                        + ", ID = " + Thread.currentThread().getId());
            });
            t.start();
        }
    }

    /**
     * ID    Name                           Daemon     State
     * ============================
     * 13    Monitor Ctrl-Break             true       RUNNABLE
     * 12    Common-Cleaner                 true       TIMED_WAITING
     * 2     Reference Handler              true       RUNNABLE
     * 14    Notification Thread            true       RUNNABLE
     * 3     Finalizer                      true       WAITING
     * 1     main                           false      RUNNABLE
     * 4     Signal Dispatcher              true       RUNNABLE
     * 7     JVMCI-native CompilerThread0   true       RUNNABLE
     * @param args
     */
    public static void main2(String[] args) {
        Map<Thread, StackTraceElement[]> allThreads = Thread.getAllStackTraces();

        System.out.printf("%-5s %-30s %-10s %-10s%n", "ID", "Name", "Daemon", "State");
        System.out.println("============================");

        for (Thread thread : allThreads.keySet()) {
            System.out.printf("%-5d %-30s %-10s %-10s%n",
                    thread.getId(),
                    thread.getName(),
                    thread.isDaemon(),
                    thread.getState());
        }
    }
}

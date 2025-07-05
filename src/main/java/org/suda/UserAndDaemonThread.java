package org.suda;

/**
 * output:
 * [守护线程] 正在运行...
 * [用户线程] 开始执行...
 * [守护线程] 正在运行...
 * [守护线程] 正在运行...
 * [守护线程] 正在运行...
 * [守护线程] 正在运行...
 * [守护线程] 正在运行...
 * [用户线程] 执行完毕！
 * [主线程] 退出 main 方法
 * [守护线程] 正在运行...
 * 说明：一旦用户线程和主线程执行完毕，JVM就会结束程序，不管守护线程是否死循环
 * 当 comment daemonThread.setDaemon(true); 那么程序会一直执行
 */
public class UserAndDaemonThread {
    public static void main(String[] args) throws InterruptedException {

        // 创建一个守护线程
        Thread daemonThread = new Thread(() -> {
            while (true) {
                System.out.println("[守护线程] 正在运行...");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        daemonThread.setDaemon(true); // 设置为守护线程
        daemonThread.start();

        // 创建一个非守护线程（用户线程）
        Thread userThread = new Thread(() -> {
            System.out.println("[用户线程] 开始执行...");
            try {
                Thread.sleep(3000); // 睡3秒
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("[用户线程] 执行完毕！");
        });
        userThread.start();

        // 主线程等待用户线程结束
        userThread.join();

        System.out.println("[主线程] 退出 main 方法");
    }
}

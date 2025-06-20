package org.suda;

public class ThreadLocalDemo {

    // 定义一个 ThreadLocal，用于保存当前线程的用户信息
    private static final ThreadLocal<String> userHolder = new ThreadLocal<>();

    public static void main(String[] args) {

        // 创建两个线程，分别设置自己的用户信息
        Thread thread1 = new Thread(() -> {
            userHolder.set("用户A");
            try {
                Thread.sleep(1000);
                System.out.println(Thread.currentThread().getName() + " 获取到的用户是: " + userHolder.get());
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                userHolder.remove(); // 防止内存泄漏
            }
        }, "线程1");

        Thread thread2 = new Thread(() -> {
            userHolder.set("用户B");
            try {
                Thread.sleep(1000);
                System.out.println(Thread.currentThread().getName() + " 获取到的用户是: " + userHolder.get());
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                userHolder.remove(); // 防止内存泄漏
            }
        }, "线程2");

        thread1.start();
        thread2.start();
    }
}


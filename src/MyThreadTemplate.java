import sun.nio.ch.Interruptible;

import java.util.concurrent.locks.LockSupport;

/**
 * Created by zhaoshengqi on 2017/7/21.
 */
public class MyThreadTemplate {
    public enum State {
        /**
         * 新建
         */
        NEW,

        /**
         * 就绪
         */
        RUNNABLE,

        /**
         * 阻塞
         * {@link Object#wait() Object.wait}.
         */
        BLOCKED,

        /**
         * 等待
         * @important 调用wait()方法引起"当前线程等待",直到另外一个线程调用notify()或notifyAll()唤醒该线程。
         * 换句话说，这个方法和wait(0)的效果一样！。
         * “当前线程”在调用wait()时，必须拥有该对象的同步锁。该线程调用wait()之后，会释放该锁；
         * 然后一直等待直到“其它线程”调用对象的同步锁的notify()或notifyAll()方法。
         * 然后，该线程继续等待直到它重新获取“该对象的同步锁”，然后就可以接着运行。
         * following methods:
         *   {@link Object#wait() Object.wait} with no timeout
         *   {@link #join(long) Thread.join} with no timeout
         *   {@link LockSupport#park() LockSupport.park}
         */
        WAITING,

        /**
         * 带等待时间的WAITING
         * <ul>
         *   <li>{@link #sleep Thread.sleep}</li>
         *   <li>{@link Object#wait(long) Object.wait} with timeout</li>
         *   <li>{@link #join(long) Thread.join} with timeout</li>
         *   <li>{@link LockSupport#parkNanos LockSupport.parkNanos}</li>
         *   <li>{@link LockSupport#parkUntil LockSupport.parkUntil}</li>
         * </ul>
         */
        TIMED_WAITING,

        /**
         * 销毁
         */
        TERMINATED;
    }


    /**
     * 启动一个新线程，新线程会执行相应的run()方法。start()不能被重复调用。
     * start()实际上是通过本地方法start0()启动线程的。
     * 而start0()会新运行一个线程，一旦得到CPU时间片,新线程会调用run()方法。
     */
    public synchronized void start() {
        /**
         * 如果线程不是"就绪状态"，则抛出异常！
         */
        if (threadStatus != 0)
            throw new IllegalThreadStateException();

        /* Notify the group that this thread is about to be started
         * so that it can be added to the group's list of threads
         * and the group's unstarted count can be decremented. */
        group.add(this);

        boolean started = false;
        try {
            start0();
            started = true;
        } finally {
            try {
                if (!started) {
                    group.threadStartFailed(this);
                }
            } catch (Throwable ignore) {
                /* do nothing. If start0 threw a Throwable then
                  it will be passed up the call stack */
            }
        }
    }

    @Override
    public void run() {
        if (target != null) {
            target.run();
        }
    }

    /**
     * 调用JNI接口
     */
    private native void start0();
    /**
     *获取当前执行线程
     */
    public static native Thread currentThread();

    /**
     * @important 调用wait()方法引起"当前线程等待",直到另外一个线程调用notify()或notifyAll()唤醒该线程。
     * 换句话说，这个方法和wait(0)的效果一样！。
     * “当前线程”在调用wait()时，必须拥有该对象的同步锁。该线程调用wait()之后，会释放该锁；
     * 然后一直等待直到“其它线程”调用对象的同步锁的notify()或notifyAll()方法。
     * 然后，该线程继续等待直到它重新获取“该对象的同步锁”，然后就可以接着运行。
     */
    public final void wait() throws InterruptedException {
        wait(0);
    }
    public final native void wait(long timeout) throws InterruptedException;
    public final native void notify();
    public final native void notifyAll();

    /**
     * yield()的作用是让步。它能让当前线程由“运行状态”进入到“就绪状态”，
     * 从而让其它具有相同优先级的等待线程获取执行权；但是，并不能保证在当前线程调用yield()之后，
     * 其它具有相同优先级的线程就一定能获得执行权；也有可能是当前线程又进入到“运行状态”继续运行！
     */
    public static native void yield();

    /**
     * 使当前线程休眠，即当前线程会从“运行状态”进入到“休眠(阻塞)状态”。
     * sleep()会指定休眠时间，线程休眠的时间会大于/等于该休眠时间；在线程重新被唤醒时，
     * 它会由“阻塞状态”变成“就绪状态”，从而等待cpu的调度执行。不会释放锁
     */
    public static native void sleep(long millis) throws InterruptedException;

    /**
     * 让“主线程”等待“子线程”结束之后才能继续运行。
     * wait()的作用是让“当前线程”等待，而这里的“当前线程”是指当前在CPU上运行的线程。
     * 所以，虽然是调用子线程的wait()方法，但是它是通过“主线程”去调用的；所以，休眠的是主线程，而不是“子线程”！
     */
    public final synchronized void join(long millis) throws InterruptedException {
        long base = System.currentTimeMillis();
        long now = 0;

        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }

        if (millis == 0) {
            while (isAlive()) {
                wait(0);
            }
        } else {
            while (isAlive()) {
                long delay = millis - now;
                if (delay <= 0) {
                    break;
                }
                wait(delay);
                now = System.currentTimeMillis() - base;
            }
        }
    }

    /**
     * interrupt()的作用是中断本线程。
     * 本线程中断自己是被允许的；其它线程调用本线程的interrupt()方法时，会通过checkAccess()检查权限。这有可能抛出SecurityException异常。
     * 如果本线程是处于阻塞状态：调用线程的wait(), wait(long)或wait(long, int)会让它进入等待(阻塞)状态，
     * 或者调用线程的join(), join(long), join(long, int), sleep(long), sleep(long, int)也会让它进入阻塞状态。
     * 若线程在阻塞状态时，调用了它的interrupt()方法，那么它的“中断状态”会被清除并且会收到一个InterruptedException异常。
     * 例如，线程通过wait()进入阻塞状态，此时通过interrupt()中断该线程；调用interrupt()会立即将线程的中断标记设为“true”，
     * 但是由于线程处于阻塞状态，所以该“中断标记”会立即被清除为“false”，同时，会产生一个InterruptedException的异常。
     * 如果线程被阻塞在一个Selector选择器中，那么通过interrupt()中断它时；线程的中断标记会被设置为true，并且它会立即从选择操作中返回。
     * 如果不属于前面所说的情况，那么通过interrupt()中断线程时，它的中断标记会被设置为“true”。
     * 中断一个“已终止的线程”不会产生任何操作。
     */
    public void interrupt() {
        if (this != Thread.currentThread())
            checkAccess();

        synchronized (blockerLock) {
            Interruptible b = blocker;
            if (b != null) {
                interrupt0();           // Just to set the interrupt flag
                b.interrupt(this);
                return;
            }
        }
        interrupt0();
    }

    /**
     * interrupted() 和 isInterrupted()都能够用于检测对象的“中断标记”。
     * interrupted()会清除中断标记(即将中断标记设为true)
     */
    public static boolean interrupted() {
        return currentThread().isInterrupted(true);
    }
    public boolean isInterrupted() {
        return isInterrupted(false);
    }

    /* Some private helper methods */
    private native void setPriority0(int newPriority);
    private native void stop0(Object o);
    private native void suspend0();
    private native void resume0();
    private native void interrupt0();
    private native void setNativeName(String name);
    public final native boolean isAlive();

    //最小优先级
    public final static int MIN_PRIORITY = 1;
    //默认优先级
    public final static int NORM_PRIORITY = 5;
    //最大优先级
    public final static int MAX_PRIORITY = 10;

    public final void setPriority(int newPriority) {
        ThreadGroup g;
        checkAccess();
        if (newPriority > MAX_PRIORITY || newPriority < MIN_PRIORITY) {
            throw new IllegalArgumentException();
        }
        if((g = getThreadGroup()) != null) {
            if (newPriority > g.getMaxPriority()) {
                newPriority = g.getMaxPriority();
            }
            setPriority0(priority = newPriority);
        }
    }


}

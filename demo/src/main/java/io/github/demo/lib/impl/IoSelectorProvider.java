package io.github.demo.lib.impl;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.demo.lib.CloseUtils;
import io.github.demo.lib.core.IoProvider;

/**
 * 注册和反注册，SocketChannel
 * <p>
 * 这里使用读写分开的selector，可以让系统去区分
 */
public class IoSelectorProvider implements IoProvider {

    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    // 是否处于注册过程
    private final AtomicBoolean inReqInput = new AtomicBoolean(false);
    private final AtomicBoolean inReqOutput = new AtomicBoolean(false);

    private final Selector readSelector;
    private final Selector writeSelector;

    private final HashMap<SelectionKey, Runnable> inputCallbackMap = new HashMap<>();
    private final HashMap<SelectionKey, Runnable> outputCallbackMap = new HashMap<>();

    private final ExecutorService inputHandlerPool;
    private final ExecutorService outputHandlerPool;

    public IoSelectorProvider() throws IOException {
        readSelector = Selector.open();
        writeSelector = Selector.open();

        inputHandlerPool = Executors.newFixedThreadPool(4, new IoProviderThreadFactory("IoProvider-Input-Thread-"));
        outputHandlerPool = Executors.newFixedThreadPool(4, new IoProviderThreadFactory("IoProvider-Output-Thread-"));

        startRead();
        startWrite();
    }

    private void startRead() {
        Thread thread = new Thread("IoSelectorProvider-ReadSelector") {
            @Override
            public void run() {
                while (!isClosed.get()) {
                    try {
                        if (readSelector.select() == 0) {
                            waitSelection(inReqInput);
                            continue;
                        }

                        Set<SelectionKey> selectionKeys = readSelector.selectedKeys();
                        for (SelectionKey selectionKey : selectionKeys) {
                            if (selectionKey.isValid()) {
                                handleSelection(selectionKey, SelectionKey.OP_READ, inputCallbackMap, inputHandlerPool);
                            }
                        }

                        // 复现3：消息到达重复提醒，读消息时未设置取消监听
                        // 消息到达后，及时处理好。下次select()自然只收到一次消息，即使没有取消监听
                        // System.out.println("有数据需要读取："+selectionKeys.size());

                        selectionKeys.clear();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        thread.setPriority(Thread.MAX_PRIORITY);// 最高优先级
        thread.start();
    }

    private void startWrite() {
        Thread thread = new Thread("IoSelectorProvider-WriteSelector") {
            @Override
            public void run() {
                while (!isClosed.get()) {
                    try {
                        if (writeSelector.select() == 0) {
                            // 如果是注册时唤醒的，那么就等注册结束再下次循环
                            waitSelection(inReqOutput);
                            continue;
                        }

                        Set<SelectionKey> selectionKeys = writeSelector.selectedKeys();
                        for (SelectionKey selectionKey : selectionKeys) {
                            if (selectionKey.isValid()) {
                                handleSelection(selectionKey, SelectionKey.OP_WRITE, outputCallbackMap, outputHandlerPool);
                            }
                        }
                        selectionKeys.clear();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        thread.setPriority(Thread.MAX_PRIORITY);// 最高优先级
        thread.start();
    }

    /**
     * 这里和readSelector.select() 处于不同线程
     * readSelector 有双线程在操作
     */
    @Override
    public boolean registerInput(SocketChannel channel, HandleInputCallback callback) {
        // channel可读时回调，可读后 readSelector.select则>0
        // 如果可读时没有取消注册，那么while里selection是线程池操作的，所以会立即返回
        // 虽然selectionKeys.clear()操作，但如果channel还有数据没有读完，那么就一直可读，select就一直大于0，就导致重复执行
        // 所以handleSelection执行时暂时断掉，不要再监听这个客户端的连接，直到处理完再加载进来
        // channel.register(readSelector, SelectionKey.OP_READ);

        return registerSelection(channel, readSelector, SelectionKey.OP_READ, inReqInput, inputCallbackMap, callback) != null;
    }

    @Override
    public boolean registerOutput(SocketChannel channel, HandleOutputCallback callback) {
        return registerSelection(channel, writeSelector, SelectionKey.OP_WRITE, inReqOutput, outputCallbackMap, callback) != null;
    }

    @Override
    public void unRegisterInput(SocketChannel channel) {
        unRegisterSelection(channel, readSelector, inputCallbackMap);
    }

    @Override
    public void unRegisterOutput(SocketChannel channel) {
        unRegisterSelection(channel, writeSelector, outputCallbackMap);
    }

    @Override
    public void close() {
        if(isClosed.compareAndSet(false, true)){
            inputHandlerPool.shutdown();
            outputHandlerPool.shutdown();
            inputCallbackMap.clear();
            outputCallbackMap.clear();

            readSelector.wakeup();
            writeSelector.wakeup();

            CloseUtils.close(readSelector, writeSelector);
        }
    }

    private static void waitSelection(final AtomicBoolean locker){
        synchronized (locker){
            // 锁定时
            if(locker.get()){
                try {
                    locker.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static SelectionKey registerSelection(SocketChannel channel, Selector selector, int registerOps,
                                 AtomicBoolean locker, HashMap<SelectionKey, Runnable> map, Runnable runnable) {
        synchronized (locker) {
            // 设置锁定状态
            locker.set(true);
            try {
                // 唤醒selector，让selector不处于阻塞状态。阻塞状态时变更时是无效的，selector还是上次的channel
                selector.wakeup();
                SelectionKey key = null;
                if (channel.isRegistered()) {// 通道是否接收过注册
                    // key.interestOps(key.readyOps() & ~keyOps); 只是取消监听，注册还在
                    // 如果只是取消监听，那么只需设置为继续监听就可以了
                    key = channel.keyFor(selector);
                    if (key != null) {
                        key.interestOps(key.readyOps() | registerOps);
                    }
                }
                if (key == null) {// 注册key
                    key = channel.register(selector, registerOps);
                    map.put(key, runnable);
                }
                return key;
            } catch(ClosedChannelException e){
                return null;
            } finally {
                // 解除锁定状态
                locker.set(false);
                try {
                    locker.notify();// 唤醒某个唤醒
                } catch (Exception e) {
                    // 如果当前没有阻塞其他线程，就会有异常
                }
            }
        }
    }

    private static void unRegisterSelection(SocketChannel channel, Selector selector, Map<SelectionKey, Runnable> map){
        if(channel .isRegistered()){
            // 通道已有注册
            SelectionKey key = channel.keyFor(selector);
            if(key != null){
                // 取消监听 取消监听的另一个方法
                // key.interestOps(key.readyOps() & ~keyOps); 在我们代码这里是一样的，因为读和写等都是分开的Selector
                key.cancel();
                map.remove(key);
                selector.wakeup();
            }
        }
    }

    private static void handleSelection(SelectionKey key, int keyOps,
                                        HashMap<SelectionKey, Runnable> map, ExecutorService pool) {
        // 取消当前连接的Selector，一直到处理完再重新注册回来
        // 复现3：消息到达重复提醒，读消息时未设置取消监听
        // 注释取消监听就可以演示出来
        key.interestOps(key.readyOps() & ~keyOps);
        Runnable runnable = null;
        try {
            runnable = map.get(key);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (runnable != null && !pool.isShutdown()) {
            pool.execute(runnable);// 异步调度读写
        }
    }

    static class IoProviderThreadFactory implements ThreadFactory {
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        IoProviderThreadFactory(String namePrefix) {
            SecurityManager var1 = System.getSecurityManager();
            this.group = var1 != null ? var1.getThreadGroup() : Thread.currentThread().getThreadGroup();
            this.namePrefix = namePrefix;
        }

        public Thread newThread(Runnable var1) {
            Thread var2 = new Thread(this.group, var1, this.namePrefix + this.threadNumber.getAndIncrement(), 0L);
            if (var2.isDaemon()) {
                var2.setDaemon(false);
            }

            if (var2.getPriority() != 5) {
                var2.setPriority(5);
            }

            return var2;
        }
    }
}

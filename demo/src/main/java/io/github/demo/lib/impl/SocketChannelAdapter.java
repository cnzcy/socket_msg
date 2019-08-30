package io.github.demo.lib.impl;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.demo.lib.CloseUtils;
import io.github.demo.lib.core.IoArgs;
import io.github.demo.lib.core.IoProvider;
import io.github.demo.lib.core.Receiver;
import io.github.demo.lib.core.Sender;

/**
 * 消息接收、发送、关闭
 * 处理一个客户端进来后的消息收发
 */
public class SocketChannelAdapter implements Sender, Receiver, Closeable {

    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final SocketChannel channel;
    private final IoProvider ioProvider;
    private final OnChannelStatusChangedListener listener;

    private IoArgs.IoArgsEventListener receiveIoEventListener;
    private IoArgs.IoArgsEventListener sendIoEventListener;

    private IoArgs receiveArgsTemp;

    public SocketChannelAdapter(SocketChannel channel, IoProvider ioProvider, OnChannelStatusChangedListener listener) throws IOException {
        this.channel = channel;
        this.ioProvider = ioProvider;
        this.listener = listener;
        channel.configureBlocking(false);
    }

    @Override
    public void setReceiveListener(IoArgs.IoArgsEventListener listener) {
        receiveIoEventListener = listener;
    }

    @Override
    public boolean receiveAsync(IoArgs args) throws IOException {
        if (isClosed.get()) {
            throw new IOException("当前管道已经关闭");
        }
        receiveArgsTemp = args;
        return ioProvider.registerInput(channel, inputCallback);
    }

    @Override
    public boolean sendAsync(IoArgs args, IoArgs.IoArgsEventListener listener) throws IOException {
        if (isClosed.get()) {
            throw new IOException("当前管道已经关闭");
        }

        sendIoEventListener = listener;
        // 注入发送的内容，在outputCallback中会再回调进来
        outputCallback.setAttach(args);
        return ioProvider.registerOutput(channel, outputCallback);
    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {// 如果时false，再更新true
            ioProvider.unRegisterInput(channel);
            ioProvider.unRegisterOutput(channel);

            CloseUtils.close(channel);
            // 通知外层，这个客户端已经关闭了
            listener.onChannelClosed(channel);
        }
    }

    // 复现3：消息到达重复提醒，读消息时未设置取消监听
    // private boolean runed;

    // 可读了
    private final IoProvider.HandleInputCallback inputCallback = new IoProvider.HandleInputCallback() {
        @Override
        protected void canProviderInput() {
            if (isClosed.get()) {
                return;
            }

            // 复现3：消息到达重复提醒，读消息时未设置取消监听
            /*if (runed) {
                return;
            }
            runed = true;
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }*/

            IoArgs args = receiveArgsTemp;
            IoArgs.IoArgsEventListener listener = SocketChannelAdapter.this.receiveIoEventListener;
            listener.onStarted(args);

            // IoArgs中进行读操作，读完成后通知外部
            try {
                if (args.readFrom(channel) > 0) {
                    listener.onCompleted(args);
                } else {
                    throw new IOException("不能读取数据");
                }
            } catch (IOException e) {
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }
    };

    // 线程池中回调的发送Runnable
    private final IoProvider.HandleOutputCallback outputCallback = new IoProvider.HandleOutputCallback() {

        @Override
        protected void canProviderOutput(Object attach) {
            if (isClosed.get()) {
                return;
            }
            // IoArgs已经放到attach中了
            IoArgs args = getAttach();
            IoArgs.IoArgsEventListener listener = sendIoEventListener;
            listener.onStarted(args);

            try {
                if (args.writeTo(channel) > 0) {
                    listener.onCompleted(args);
                } else {
                    throw new IOException("不能写入数据");
                }
            } catch (IOException e) {
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }
    };

    public interface OnChannelStatusChangedListener {
        void onChannelClosed(SocketChannel channel);
    }
}

package io.github.demo.lib.impl;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.demo.lib.CloseUtils;
import io.github.demo.lib.core.IoArgs;
import io.github.demo.lib.core.SendDispatcher;
import io.github.demo.lib.core.SendPacket;
import io.github.demo.lib.core.Sender;

/**
 * 发送数据的调度实现
 */
public class AsyncSendDispatcher implements SendDispatcher, IoArgs.IoArgsEventProcessor {

    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    // 发送者
    private final Sender sender;
    // 发送的过程中有消息进来，丢到对列中等待发送
    private final Queue<SendPacket> queue = new ConcurrentLinkedQueue<>();
    // 维护发送状态
    private final AtomicBoolean isSending = new AtomicBoolean();

    // 发送的packet可能比一个IoArgs大，这里还定义了大小，发送的进度
    private IoArgs ioArgs = new IoArgs();
    private SendPacket<?> packetTemp;
    private long total;
    private long position;

    private ReadableByteChannel packetChannel;

    public AsyncSendDispatcher(Sender sender) {
        this.sender = sender;
        sender.setSendListener(this);
    }

    @Override
    public void send(SendPacket packet) {
        queue.offer(packet);
        // 当前不在发送中，激活起来
        if (isSending.compareAndSet(false, true)) {
            sendNextPacket();
        }
    }

    private SendPacket takePacket() {
        SendPacket packet = queue.poll();
        if (packet != null && packet.isCanceled()) {
            // 如果已经被取消了，则不用发送
            return takePacket();
        }
        return packet;
    }

    private void sendNextPacket() {
        // 发送下一个前，如果上一个还不为null则先关闭，不影响当前包发送
        SendPacket temp = packetTemp;
        if (temp != null) {
            CloseUtils.close(temp);
        }

        SendPacket packet = packetTemp = takePacket();
        if (packet == null) {// 对列已经空了，取消状态发送
            isSending.set(false);
            return;
        }
        total = packet.length();
        position = 0;

        sendCurrentPacket();
    }

    // 发送当前的packet
    private void sendCurrentPacket() {
        if (position >= total) {
            completePacket(position == total);
            // 数据发送完成，发送下一个
            sendNextPacket();
            return;
        }

        // 没有发送完成继续发送
        try {
            sender.postSendAsync();
        } catch (IOException e) {
            closeAndNotify();
        }
    }

    /**
     * packet发送完成
     * 不一定都是成功的
     */
    private void completePacket(boolean isSuccessed){
        SendPacket packet = packetTemp;
        if(packet == null){
            return;
        }
        CloseUtils.close(packet);
        CloseUtils.close(packetChannel);

        packetTemp = null;
        packetChannel = null;
        total = 0;
        position = 0;
    }

    //关闭并通知外层
    private void closeAndNotify() {
        CloseUtils.close(this);
    }

    @Override
    public void cancel(SendPacket packet) {

    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            isSending.set(false);
            completePacket(false);// 非正常完成操作
        }
    }

    @Override
    public IoArgs provideIoArgs() {
        IoArgs args = ioArgs;
        if(packetChannel == null){
            // 首次操作，通道还不存在
            packetChannel = Channels.newChannel(packetTemp.open());
            // 消息头
            args.limit(4);
            args.writeLength((int) packetTemp.length());
        }else{
            // 消息体
            // 自身最大容量，容量不会超，这里可以强转
            args.limit((int) Math.min(args.capacity(), total - position));
            try {
                int count = args.readFrom(packetChannel);
                position += count;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        return args;
    }

    @Override
    public void onConsumeFailed(IoArgs args, Exception e) {
        e.printStackTrace();
    }

    @Override
    public void onConsumeCompleted(IoArgs args) {
        // 有可能当前包还没发送完成，被拆包了，继续发送
        sendCurrentPacket();
    }
}

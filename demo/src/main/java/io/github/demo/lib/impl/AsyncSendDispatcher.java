package io.github.demo.lib.impl;

import java.io.IOException;
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
public class AsyncSendDispatcher implements SendDispatcher {

    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    // 发送者
    private final Sender sender;
    // 发送的过程中有消息进来，丢到对列中等待发送
    private final Queue<SendPacket> queue = new ConcurrentLinkedQueue<>();
    // 维护发送状态
    private final AtomicBoolean isSending = new AtomicBoolean();

    // 发送的packet可能比一个IoArgs大，这里还定义了大小，发送的进度
    private IoArgs ioArgs = new IoArgs();
    private SendPacket packetTemp;
    private int total;
    private int position;

    public AsyncSendDispatcher(Sender sender) {
        this.sender = sender;
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
        IoArgs args = ioArgs;

        // 开始，清理
        args.startWriting();
        if (position >= total) {
            // 数据发送完成，发送下一个
            sendNextPacket();
            return;
        }
        if (position == 0) {
            // 首包，刚发送，需要携带长度信息
            args.writeLength(total);
        }

        byte[] bytes = packetTemp.bytes();
        // bytes 写入到IoArgs中
        int count = args.readFrom(bytes, position);
        position += count;

        // 完成一个发送packet的封装
        args.finishWriting();

        try {
            sender.sendAsync(args, ioArgsEventListener);
        } catch (IOException e) {
            closeAndNotify();
        }
    }

    //关闭并通知外层
    private void closeAndNotify() {
        CloseUtils.close(this);
    }

    // 进度的回调
    private final IoArgs.IoArgsEventListener ioArgsEventListener = new IoArgs.IoArgsEventListener() {
        @Override
        public void onStarted(IoArgs args) {

        }

        @Override
        public void onCompleted(IoArgs args) {
            // 有可能当前包还没发送完成，被拆包了，继续发送
            sendCurrentPacket();
        }
    };

    @Override
    public void cancel(SendPacket packet) {

    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            isSending.set(false);
            SendPacket packet = this.packetTemp;
            if (packet != null) {
                packetTemp = null;
                CloseUtils.close(packet);
            }
        }
    }
}

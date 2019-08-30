package io.github.demo.lib.impl;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.demo.lib.CloseUtils;
import io.github.demo.lib.core.IoArgs;
import io.github.demo.lib.core.ReceiveDispatcher;
import io.github.demo.lib.core.ReceivePacket;
import io.github.demo.lib.core.Receiver;
import io.github.demo.lib.packet.StringReceivePacket;

/**
 * 接收数据的调度
 */
public class AsyncReceiveDispatcher implements ReceiveDispatcher {

    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    // 接收者
    private final Receiver receiver;
    private final ReceivePacketCallback callback;

    private IoArgs ioArgs = new IoArgs();
    // 当前接收的包
    private ReceivePacket packetTemp;
    // 数据先接收到buffer中，再写到packet
    private byte[] buffer;
    private int total;
    private int position;

    public AsyncReceiveDispatcher(Receiver receiver, ReceivePacketCallback callback) {
        this.receiver = receiver;
        this.receiver.setReceiveListener(ioArgsEventListener);
        this.callback = callback;
    }

    @Override
    public void start() {
        registerReceive();
    }

    // 开始接收操作
    private void registerReceive() {
        try {
            receiver.receiveAsync(ioArgs);
        } catch (IOException e) {
            closeAndNotify();
        }
    }

    // 异常时关闭
    private void closeAndNotify() {
        CloseUtils.close(this);
    }

    @Override
    public void stop() {

    }

    @Override
    public void close() throws IOException {
        if(isClosed.compareAndSet(false, true)){
            ReceivePacket packet = this.packetTemp;
            if(packet != null){
                packetTemp = null;
                CloseUtils.close(packet);
            }
        }
    }

    private IoArgs.IoArgsEventListener ioArgsEventListener = new IoArgs.IoArgsEventListener() {
        @Override
        public void onStarted(IoArgs args) {
            // 接收数据前
            int receiveSize;// 可以接收的数据大小
            if (packetTemp == null) {
                // 先接收数据头
                receiveSize = 4;// int 类型
            } else {
                // 还需要接收的大小，和还剩的容量
                receiveSize = Math.min(total - position, args.capacity());
            }
            // 设置本次接收数据大小
            args.limit(receiveSize);
        }

        @Override
        public void onCompleted(IoArgs args) {
            // 有数据来时先解析数据
            assemblePacket(args);
            // 接收下一条数据
            registerReceive();
        }
    };

    private void assemblePacket(IoArgs args) {
        if (packetTemp == null) {// 初始时
            int length = args.readLength();// 数据有多长是保存在前4字节中的
            packetTemp = new StringReceivePacket(length);
            buffer = new byte[length];
            total = length;
            position = 0;
        }

        // 开始读取，数据写到buffer
        int count = args.writeTo(buffer, 0);
        // 有数据，写入到Packet
        if (count > 0) {
            packetTemp.save(buffer, count);
            position += count;

            if (position == total) {
                // 读取完成了
                completePacket();
                packetTemp = null;
            }
        }
    }

    // 关闭并通知外层接收好了
    private void completePacket() {
        ReceivePacket packet = this.packetTemp;
        CloseUtils.close(packet);
        callback.onReceivePacketCompleted(packet);
    }
}

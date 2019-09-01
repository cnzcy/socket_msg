package io.github.demo.lib.impl;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.demo.lib.CloseUtils;
import io.github.demo.lib.core.IoArgs;
import io.github.demo.lib.core.Packet;
import io.github.demo.lib.core.ReceiveDispatcher;
import io.github.demo.lib.core.ReceivePacket;
import io.github.demo.lib.core.Receiver;
import io.github.demo.lib.packet.StringReceivePacket;

/**
 * 接收数据的调度
 */
public class AsyncReceiveDispatcher implements ReceiveDispatcher, IoArgs.IoArgsEventProcessor {

    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    // 接收者
    private final Receiver receiver;
    private final ReceivePacketCallback callback;

    private IoArgs ioArgs = new IoArgs();
    // 当前接收的包
    private ReceivePacket<?, ?> packetTemp;

    private WritableByteChannel packetChannel;
    private long total;
    private long position;

    public AsyncReceiveDispatcher(Receiver receiver, ReceivePacketCallback callback) {
        this.receiver = receiver;
        this.receiver.setReceiveListener(this);
        this.callback = callback;
    }

    @Override
    public void start() {
        registerReceive();
    }

    // 开始接收操作
    private void registerReceive() {
        try {
            receiver.postReceiveAsync();
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
        if (isClosed.compareAndSet(false, true)) {
            completePacket(false);
        }
    }

    private void assemblePacket(IoArgs args) {
        if (packetTemp == null) {// 初始时
            int length = args.readLength();// 数据有多长是保存在前4字节中的
            // 接收的类型
            byte type = length > 200 ? Packet.TYPE_STREAM_FILE : Packet.TYPE_MEMORY_STRING;

            // 当前一个新的Packet到达
            packetTemp = callback.onArrivedNewPacket(type, length);
            // 初始化通道
            packetChannel = Channels.newChannel(packetTemp.open());
            total = length;
            position = 0;
        }

        // 开始读取，数据写到buffer
        try {
            int count = args.writeTo(packetChannel);// IoArgs写入到通道，写失败肯定抛异常
            position += count;

            if (position == total) {
                // 读取完成了
                completePacket(true);
            }
        } catch (IOException e) {
            e.printStackTrace();
            completePacket(false);
        }
    }

    // 关闭并通知外层接收好了
    private void completePacket(boolean isSuccessed) {
        ReceivePacket packet = this.packetTemp;
        CloseUtils.close(packet);
        packetTemp = null;

        WritableByteChannel channel = this.packetChannel;
        CloseUtils.close(channel);
        packetChannel = null;

        if (packet != null) {
            callback.onReceivePacketCompleted(packet);
        }
    }

    @Override
    public IoArgs provideIoArgs() {
        IoArgs args = ioArgs;// 再从网络读取时复用了
        // 接收数据前
        int receiveSize;// 可以接收的数据大小
        if (packetTemp == null) {
            // 先接收数据头
            receiveSize = 4;// int 类型
        } else {
            // 还需要接收的大小，和还剩的容量
            receiveSize = (int) Math.min(total - position, args.capacity());
        }
        // 设置本次接收数据大小
        args.limit(receiveSize);
        return args;
    }

    @Override
    public void onConsumeFailed(IoArgs args, Exception e) {
        e.printStackTrace();
    }

    @Override
    public void onConsumeCompleted(IoArgs args) {
        assemblePacket(args);
        registerReceive();
    }
}

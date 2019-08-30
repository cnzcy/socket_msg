package io.github.demo.lib.core;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * 封装ByteBuffer
 */
public class IoArgs {
    // 复现1：消息不全
    // 发送超过4个byte的长度就复现了
    // 4864728b-e623-4992-a3d3-8df68e7d7255:123
    // 长度：3
    // 4864728b-e623-4992-a3d3-8df68e7d7255:567
    // 长度：3
    // 4864728b-e623-4992-a3d3-8df68e7d7255:90
    // 长度：3
    // 而实际发送的是1234567890
    // private byte[] byteBuffer = new byte[4];
    private int limit = 256;
    private byte[] byteBuffer = new byte[256];
    private ByteBuffer buffer = ByteBuffer.wrap(byteBuffer);

    // bytes中读数据，位移表示已经读的
    public int readFrom(byte[] bytes, int offset) {
        // 操作的size大小  最多可读  与   最大容纳大小
        int size = Math.min(bytes.length - offset, buffer.remaining());
        buffer.put(bytes, offset, size);
        return size;
    }

    // 写数据到bytes中
    public int writeTo(byte[] bytes, int offset) {
        int size = Math.min(bytes.length - offset, buffer.remaining());
        buffer.get(bytes, offset, size);
        return size;
    }

    // Channel中读数据
    public int readFrom(SocketChannel channel) throws IOException {
        // 开始将读到的数据写入IoArgs
        startWriting();

        int bytesProducted = 0;// 当前生产的数
        while(buffer.hasRemaining()){// 还有容量
            int len = channel.read(buffer);
            if(len < 0){
                throw new EOFException("读取结束符异常");
            }
            bytesProducted += len;
        }

        finishWriting();
        return bytesProducted;
    }

    // 写数据到Channel中
    public int writeTo(SocketChannel channel) throws IOException {
        // buffer中还有数据，就往外写
        int bytesProducted = 0;// 当前生产的数
        while(buffer.hasRemaining()){// 还有容量
            int len = channel.write(buffer);
            if(len < 0){
                throw new EOFException("读取结束符异常");
            }
            bytesProducted += len;
        }
        return bytesProducted;
    }

    /**
     * 开始写入数据到IoArgs
     */
    public void startWriting(){
        buffer.clear();
        // 定义容纳的区间
        buffer.limit(limit);
    }

    /**
     * 写入到IoArgs完成
     */
    public void finishWriting(){
        buffer.flip();// 反转，以便可以读出来
    }

    // buffer可写的区间，就是根据消息长度来，不是本条消息，本次不希望读进来
    public void limit(int limit){
        this.limit = limit;
    }

    // 包的长度
    public void writeLength(int total) {
        buffer.putInt(total);
    }

    // 读长度
   public int readLength(){
        return buffer.getInt();
    }

    // 剩余的容量
    public int capacity() {
        return buffer.capacity();
    }

    public interface IoArgsEventListener {
        void onStarted(IoArgs args);

        void onCompleted(IoArgs args);
    }
}

package io.github.demo.lib.core;

import java.io.Closeable;
import java.io.IOException;

/**
 * 数据包
 * 定义数据类型和长度
 */
public abstract class Packet<Stream extends Closeable> implements Closeable {
    protected Stream stream;

    // BYTES 类型
    public static final byte TYPE_MEMORY_BYTES = 1;
    // String 类型
    public static final byte TYPE_MEMORY_STRING = 2;
    // 文件 类型
    public static final byte TYPE_STREAM_FILE = 3;
    // 长链接流 类型
    public static final byte TYPE_STREAM_DIRECT = 4;

    protected long length;

    /**
     * 类型，直接通过方法得到:
     * <p>
     * {@link #TYPE_MEMORY_BYTES}
     * {@link #TYPE_MEMORY_STRING}
     * {@link #TYPE_STREAM_FILE}
     * {@link #TYPE_STREAM_DIRECT}
     *
     * @return 类型
     */
    public abstract byte type();

    public long length(){
        return length;
    }

    public final Stream open(){
        if(stream == null){
            stream = createStream();
        }
        return stream;
    }

    protected abstract Stream createStream();
    protected void closeStream(Stream stream) throws IOException {
        stream.close();
    }

    @Override
    public final void close() throws IOException {
        if(stream != null){
            closeStream(stream);
            stream = null;
        }
    }
}

package io.github.demo.lib.core;

import java.io.Closeable;
import java.io.IOException;

/**
 * 数据包
 * 定义数据类型和长度
 */
public abstract class Packet<T extends Closeable> implements Closeable {
    protected T stream;

    protected byte type;
    protected long length;

    public byte type(){
        return type;
    }

    public long length(){
        return length;
    }

    public final T open(){
        if(stream == null){
            stream = createStream();
        }
        return stream;
    }

    protected abstract T createStream();
    protected void closeStream(T stream) throws IOException {
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

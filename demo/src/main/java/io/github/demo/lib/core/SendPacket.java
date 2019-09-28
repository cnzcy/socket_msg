package io.github.demo.lib.core;


import java.io.InputStream;

/**
 * 发送的包
 */
public abstract class SendPacket<T extends InputStream> extends Packet<T>{

    private boolean isCanceled;

    /**
     * 是否已取消
     * @return
     */
    public boolean isCanceled(){
        return isCanceled;
    }

    /**
     * 设置取消发送标记
     */
    public void cancel() {
        isCanceled = true;
    }
}

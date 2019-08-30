package io.github.demo.lib.core;

/**
 * 发送的包
 */
public abstract class SendPacket extends Packet{
    private boolean isCanceled;

    public abstract byte[] bytes();

    /**
     * 是否已取消
     * @return
     */
    public boolean isCanceled(){
        return isCanceled;
    }

}

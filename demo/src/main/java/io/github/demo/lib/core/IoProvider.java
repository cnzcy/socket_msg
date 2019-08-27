package io.github.demo.lib.core;

import java.io.Closeable;
import java.nio.channels.SocketChannel;

/**
 * 提供 SocketChannel读写
 */
public interface IoProvider extends Closeable {
    boolean registerInput(SocketChannel channel, HandleInputCallback callback);

    boolean registerOutput(SocketChannel channel, HandleOutputCallback callback);

    void unRegisterInput(SocketChannel channel);

    void unRegisterOutput(SocketChannel channel);

    abstract class HandleInputCallback implements Runnable {
        @Override
        public final void run() {
            canProviderInput();
        }

        /**
         * 可以提供输入了，就是可读了
         */
        protected abstract void canProviderInput();
    }

    abstract class HandleOutputCallback implements Runnable {
        private Object attach;

        @Override
        public final void run() {
            canProviderOutput(attach);
        }

        public final void setAttach(Object attach) {
            this.attach = attach;
        }

        /**
         * 可以写了，包括一个附件
         * @param attach
         */
        protected abstract void canProviderOutput(Object attach);
    }

}

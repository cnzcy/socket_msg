package io.github.demo.lib.core;

import java.io.Closeable;
import java.io.IOException;

public interface Receiver extends Closeable {

    void setReceiveListener(IoArgs.IoArgsEventProcessor processor);

    boolean postReceiveAsync() throws IOException;
}

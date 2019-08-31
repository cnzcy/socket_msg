package io.github.demo.lib.core;

import java.io.Closeable;
import java.io.IOException;

public interface Sender extends Closeable {

    void setSendListener(IoArgs.IoArgsEventProcessor processor);

    boolean postSendAsync() throws IOException;
}

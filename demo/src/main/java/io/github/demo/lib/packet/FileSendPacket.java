package io.github.demo.lib.packet;

import java.io.File;
import java.io.FileInputStream;

import io.github.demo.lib.core.SendPacket;

public class FileSendPacket extends SendPacket<FileInputStream> {

    public FileSendPacket(File file) {
        this.length = file.length();
    }

    @Override
    protected FileInputStream createStream() {
        return null;
    }
}

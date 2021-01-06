package com.jkbff.budabot;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class FilterUnicodeInputStream extends FilterInputStream {
    private final int[] invalidBytes = {0x3, 0x5, 0x7, 0x8, 0x10, 0xc, 0x18};
    private final byte replaceChar = '?';

    public FilterUnicodeInputStream(InputStream in) {
        super(in);
    }

    @Override
    public int read() throws IOException {
        int read = super.read();
        for (int x : invalidBytes) {
            if (read == x) {
                read = replaceChar;
                break;
            }
        }
        return read;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int num = super.read(b, off, len);
        for (int i = off; i < num; i++) {
            for (int x : invalidBytes) {
                if (b[i] == x) {
                    b[i] = replaceChar;
                    break;
                }
            }
        }

        return num;
    }
}

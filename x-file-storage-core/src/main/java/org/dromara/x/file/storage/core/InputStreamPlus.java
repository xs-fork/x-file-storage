package org.dromara.x.file.storage.core;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import lombok.Getter;

/**
 * 增强版本的 InputStream ，可以带进度监听、计算哈希等功能
 */
@Getter
public class InputStreamPlus extends FilterInputStream {
    protected boolean readFlag;
    protected boolean finishFlag;
    protected long progressSize;
    protected final Long allSize;
    protected final ProgressListener listener;
    protected int markFlag;

    public InputStreamPlus(InputStream in, ProgressListener listener, Long allSize) {
        super(in);
        this.listener = listener;
        this.allSize = allSize;
    }

    @Override
    public long skip(long n) throws IOException {
        long skip = super.skip(n);
        onProgress(skip);
        return skip;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        onProgress(b == -1 ? -1 : 1);
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        onStart();
        int bytes = super.read(b, off, len);
        onProgress(bytes);
        return bytes;
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public synchronized void mark(int readlimit) {
        super.mark(readlimit);
        this.markFlag++;
    }

    @Override
    public synchronized void reset() throws IOException {
        super.reset();
        this.markFlag--;
    }

    /**
     * 触发开始
     */
    private void onStart() {
        if (this.markFlag > 0) return;
        if (this.readFlag) return;
        this.readFlag = true;
        if (this.listener != null) this.listener.start();
    }

    /**
     * 触发进度变动
     */
    protected void onProgress(long size) {
        if (this.markFlag > 0) return;
        if (size > 0) {
            progressSize += size;
            if (this.listener != null) this.listener.progress(progressSize, allSize);
        } else if (size < 0) {
            onFinish();
        }
    }

    /**
     * 触发读取完毕
     */
    private void onFinish() {
        if (this.markFlag > 0) return;
        if (this.finishFlag) return;
        this.finishFlag = true;
        if (this.listener != null) this.listener.finish();
    }
}

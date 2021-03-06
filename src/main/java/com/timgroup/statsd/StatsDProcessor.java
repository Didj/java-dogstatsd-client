package com.timgroup.statsd;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class StatsDProcessor implements Runnable {
    protected static final Charset MESSAGE_CHARSET = Charset.forName("UTF-8");

    protected static final String MESSAGE_TOO_LONG = "Message longer than size of sendBuffer";
    protected static final int WAIT_SLEEP_MS = 10;  // 10 ms would be a 100HZ slice

    protected final StatsDClientErrorHandler handler;

    protected final BufferPool bufferPool;
    protected final BlockingQueue<ByteBuffer> outboundQueue; // FIFO queue with outbound buffers
    protected final ExecutorService executor;
    protected final CountDownLatch endSignal;

    protected final int workers;
    protected final int qcapacity;

    protected volatile boolean shutdown;

    protected abstract class ProcessingTask implements Runnable {
        protected StringBuilder builder = new StringBuilder();
        protected CharBuffer buffer = CharBuffer.wrap(builder);
        protected final CharsetEncoder utf8Encoder = MESSAGE_CHARSET.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);

        @Override
        public abstract void run();

        protected void writeBuilderToSendBuffer(ByteBuffer sendBuffer) {

            int length = builder.length();
            // use existing charbuffer if possible, otherwise re-wrap
            if (length <= buffer.capacity()) {
                buffer.limit(length).position(0);
            } else {
                buffer = CharBuffer.wrap(builder);
            }

            if (utf8Encoder.encode(buffer, sendBuffer, true) == CoderResult.OVERFLOW) {
                throw new BufferOverflowException();
            }
        }
    }


    StatsDProcessor(final int queueSize, final StatsDClientErrorHandler handler,
            final int maxPacketSizeBytes, final int poolSize, final int workers)
            throws Exception {

        this.handler = handler;
        this.workers = workers;
        this.qcapacity = queueSize;

        this.executor = Executors.newFixedThreadPool(workers);
        this.bufferPool = new BufferPool(poolSize, maxPacketSizeBytes, true);
        this.outboundQueue = new ArrayBlockingQueue<>(poolSize);
        this.endSignal = new CountDownLatch(workers);
    }

    StatsDProcessor(final StatsDProcessor processor)
            throws Exception {

        this.handler = processor.handler;
        this.workers = processor.workers;
        this.qcapacity = processor.getQcapacity();

        this.executor = Executors.newFixedThreadPool(this.workers);
        this.bufferPool = new BufferPool(processor.bufferPool);
        this.outboundQueue = new ArrayBlockingQueue<>(this.bufferPool.getSize());
        this.endSignal = new CountDownLatch(this.workers);
    }

    protected abstract ProcessingTask createProcessingTask();

    protected abstract boolean send(final Message message);

    public BufferPool getBufferPool() {
        return this.bufferPool;
    }

    public BlockingQueue<ByteBuffer> getOutboundQueue() {
        return this.outboundQueue;
    }

    public int getQcapacity() {
        return this.qcapacity;
    }

    @Override
    public void run() {

        for (int i = 0 ; i < workers ; i++) {
            executor.submit(createProcessingTask());
        }

        boolean done = false;
        while (!done) {
            try {
                endSignal.await();
                done = true;
                executor.shutdown();
            } catch (final InterruptedException e) {
                // NOTHING
            }
        }
    }

    boolean isShutdown() {
        return shutdown;
    }

    void shutdown() {
        shutdown = true;
    }
}

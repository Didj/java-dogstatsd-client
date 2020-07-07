package com.timgroup.statsd;

import java.net.SocketOption;

public final class UnixSocketOptions {

    private UnixSocketOptions() {
        // Avoid instantiation.
    }

    /**
     * Get/Set size of the socket send buffer.
     */
    public static final SocketOption<Integer> SO_SNDBUF = new CustomSocketOption<>("SO_SNDBUF", Integer.class);

    /**
     * Get/Set send timeout.
     */
    public static final SocketOption<Integer> SO_SNDTIMEO = new CustomSocketOption<>("SO_SNDTIMEO", Integer.class);

    private static class CustomSocketOption<T> implements SocketOption<T> {

        private final String name;
        private final Class<T> type;

        CustomSocketOption(String name, Class<T> type) {
            this.name = name;
            this.type = type;
        }

        @Override public String name() { return name; }
        @Override public Class<T> type() { return type; }
        @Override public String toString() { return name; }
    }
}
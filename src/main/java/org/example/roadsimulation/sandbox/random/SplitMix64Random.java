package org.example.roadsimulation.sandbox.random;

import java.util.Random;

/**
 * {@link Random} compatibility adapter backed exclusively by the persisted
 * {@link SplitMix64V1} algorithm.
 *
 * <p>This is used only where existing optimizer APIs require {@code Random}.
 * It must not be replaced with the JDK implementation because that would
 * silently change the sandbox random protocol.</p>
 */
public final class SplitMix64Random extends Random {
    private transient SplitMix64V1 delegate;

    public SplitMix64Random(long seed) {
        super(0L);
        this.delegate = new SplitMix64V1(seed);
    }

    @Override
    protected int next(int bits) {
        if (bits < 0 || bits > 32) {
            throw new IllegalArgumentException("bits must be between 0 and 32");
        }
        if (bits == 0) {
            return 0;
        }
        return (int) (delegate.nextLong() >>> (64 - bits));
    }

    @Override
    public long nextLong() {
        return delegate.nextLong();
    }

    @Override
    public int nextInt() {
        return (int) delegate.nextLong();
    }

    @Override
    public int nextInt(int bound) {
        return delegate.nextInt(bound);
    }

    @Override
    public double nextDouble() {
        return delegate.nextDouble();
    }

    @Override
    public boolean nextBoolean() {
        return delegate.nextLong() < 0L;
    }

    @Override
    public float nextFloat() {
        return (float) ((delegate.nextLong() >>> 40) * 0x1.0p-24);
    }

    @Override
    public void nextBytes(byte[] bytes) {
        if (bytes == null) {
            throw new NullPointerException("bytes must not be null");
        }
        int index = 0;
        while (index < bytes.length) {
            long value = delegate.nextLong();
            for (int offset = 0; offset < Long.BYTES && index < bytes.length; offset++) {
                bytes[index++] = (byte) value;
                value >>>= Byte.SIZE;
            }
        }
    }

    @Override
    public void setSeed(long seed) {
        // Random's constructor invokes this before delegate exists. Subsequent
        // reseeding is intentionally supported without changing the algorithm.
        if (delegate != null) {
            delegate = new SplitMix64V1(seed);
        }
    }
}

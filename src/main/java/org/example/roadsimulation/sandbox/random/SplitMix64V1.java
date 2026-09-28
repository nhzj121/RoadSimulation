package org.example.roadsimulation.sandbox.random;

/** A small, explicitly versioned PRNG whose algorithm is owned by the sandbox contract. */
public final class SplitMix64V1 {
    private static final long GAMMA = 0x9E3779B97F4A7C15L;
    private static final double DOUBLE_UNIT = 0x1.0p-53;

    private long state;

    public SplitMix64V1(long seed) {
        this.state = seed;
    }

    public long nextLong() {
        long value = state += GAMMA;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        long bits;
        long value;
        do {
            bits = nextLong() >>> 1;
            value = bits % bound;
        } while (bits - value + (bound - 1L) < 0L);
        return (int) value;
    }

    public double nextDouble() {
        return (nextLong() >>> 11) * DOUBLE_UNIT;
    }
}

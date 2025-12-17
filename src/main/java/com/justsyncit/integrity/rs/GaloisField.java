/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.justsyncit.integrity.rs;

/**
 * Implementation of Galois Field Arithmetic GF(2^8).
 * This class provides basic arithmetic operations (add, sub, mul, div)
 * over the finite field GF(256), which is essential for Reed-Solomon coding.
 * <p>
 * It uses the primitive polynomial x^8 + x^4 + x^3 + x^2 + 1 (0x11D).
 * Precomputed log and exp tables are used for fast multiplication and division.
 */
public final class GaloisField {

    public static final int FIELD_SIZE = 256;
    public static final int PRIMITIVE_POLYNOMIAL = 0x11D; // x^8 + x^4 + x^3 + x^2 + 1

    private final int[] expTable = new int[FIELD_SIZE * 2];
    private final int[] logTable = new int[FIELD_SIZE];

    private static final GaloisField INSTANCE = new GaloisField();

    private GaloisField() {
        // Initialize tables
        int x = 1;
        for (int i = 0; i < FIELD_SIZE - 1; i++) {
            expTable[i] = x;
            logTable[x] = i;

            x <<= 1;
            if ((x & 0x100) != 0) { // If degree is 8, reduce by primitive polynomial
                x ^= PRIMITIVE_POLYNOMIAL;
            }
        }
        // Duplicate exp table to simplify multiplication logic (avoid modulo)
        for (int i = 0; i < FIELD_SIZE - 1; i++) {
            expTable[FIELD_SIZE - 1 + i] = expTable[i];
        }
        // Special case for 0 (no log) - strictly speaking undefined, usually handled by
        // checks
    }

    public static GaloisField getInstance() {
        return INSTANCE;
    }

    /**
     * Adds two elements in GF(2^8). Addition is XOR.
     */
    public int add(int a, int b) {
        return a ^ b;
    }

    /**
     * Subtracts two elements in GF(2^8). Subtraction is same as addition (XOR).
     */
    public int sub(int a, int b) {
        return a ^ b;
    }

    /**
     * Multiplies two elements in GF(2^8).
     */
    public int mul(int a, int b) {
        if (a == 0 || b == 0) {
            return 0;
        }
        return expTable[logTable[a & 0xFF] + logTable[b & 0xFF]];
    }

    /**
     * Divides two elements in GF(2^8).
     */
    public int div(int a, int b) {
        if (b == 0) {
            throw new ArithmeticException("Division by zero in GF(2^8)");
        }
        if (a == 0) {
            return 0;
        }
        // Our double exp table helps here, but standard formula is log[a] - log[b] +
        // (SIZE-1)
        // With expanded table: (log[a] - log[b] + 255) % 255
        // Or simpler: expTable[(log[a] + 255 - log[b]) % 255]
        // But we need to be careful with negative results if we just subtract.
        // Let's stick to standard safe logic:
        int logA = logTable[a & 0xFF];
        int logB = logTable[b & 0xFF];
        int logResult = logA - logB;
        if (logResult < 0) {
            logResult += (FIELD_SIZE - 1);
        }
        return expTable[logResult];
    }

    /**
     * Returns a^b (power).
     */
    public int pow(int a, int b) {
        if (b == 0)
            return 1;
        if (a == 0)
            return 0;
        if (b == 0)
            return 1;
        int logA = logTable[a & 0xFF];
        int logRes = (logA * b) % (FIELD_SIZE - 1);
        return expTable[logRes];
    }

    /**
     * Inverse of a in GF(2^8).
     */
    public int inverse(int a) {
        if (a == 0) {
            throw new ArithmeticException("Zero has no inverse");
        }
        return expTable[(FIELD_SIZE - 1) - logTable[a & 0xFF]];
    }
}

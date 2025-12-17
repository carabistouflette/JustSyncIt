package com.justsyncit.integrity.rs;

import java.util.Arrays;

/**
 * Reed-Solomon Error Correction Codec.
 * <p>
 * This class implements Reed-Solomon encoding and decoding algorithms
 * using the {@link GaloisField} arithmetic. It supports erasing coding
 * (restoring missing shards if positions are known) which is ideal for
 * storage systems where we know which chunk is missing/corrupted (by checksum).
 * <p>
 * Based on VanderMonde matrix approach.
 */
public final class ReedSolomon {

    private final GaloisField gf;
    private final int dataShards;
    private final int parityShards;
    private final int totalShards;
    private final byte[][] encodingMatrix;

    /**
     * Creates a new Reed-Solomon codec.
     *
     * @param dataShards   number of data shards (k)
     * @param parityShards number of parity shards (m)
     */
    public ReedSolomon(int dataShards, int parityShards) {
        if (dataShards <= 0 || parityShards <= 0) {
            throw new IllegalArgumentException("Shards must be positive");
        }
        if (dataShards + parityShards > 256) {
            throw new IllegalArgumentException("Total shards cannot exceed 256 for GF(2^8)");
        }

        this.gf = GaloisField.getInstance();
        this.dataShards = dataShards;
        this.parityShards = parityShards;
        this.totalShards = dataShards + parityShards;
        this.encodingMatrix = buildEncodingMatrix(dataShards, parityShards);
    }

    /**
     * Encodes parity for a set of data shards.
     * <p>
     * Input: shards[0..dataShards-1] must contain data.
     * Output: shards[dataShards..totalShards-1] will be filled with parity.
     *
     * @param shards array of byte arrays (chunks). All must be same length.
     */
    public void encodeParity(byte[][] shards, int shardSize) {
        checkShards(shards);

        // For each parity shard
        for (int i = 0; i < parityShards; i++) {
            byte[] outputShard = shards[dataShards + i];
            byte[] matrixRow = encodingMatrix[i];

            // Initialize parity shard to 0
            Arrays.fill(outputShard, 0, shardSize, (byte) 0);

            // Calculate parity byte by byte (or better: word by word, but we do naive
            // byte-loop first)
            // Optimization: unroll inner loop?
            // Better optimization: iterate over input shards and XOR accumulate into output

            for (int inputIdx = 0; inputIdx < dataShards; inputIdx++) {
                byte[] inputShard = shards[inputIdx];
                int matrixVal = matrixRow[inputIdx] & 0xFF; // Coefficient from encoding matrix

                if (matrixVal == 0)
                    continue; // Nothing to add

                if (matrixVal == 1) {
                    // Just XOR
                    for (int byteIdx = 0; byteIdx < shardSize; byteIdx++) {
                        outputShard[byteIdx] ^= inputShard[byteIdx];
                    }
                } else {
                    // Multiply and XOR
                    // This is slow, typically we use multiplication tables for fixed coefficients
                    // in a loop
                    // But for now, direct GF mul call is safe if slow.
                    // To optimize: verify performance later.
                    for (int byteIdx = 0; byteIdx < shardSize; byteIdx++) {
                        outputShard[byteIdx] = (byte) (outputShard[byteIdx] ^ gf.mul(inputShard[byteIdx], matrixVal));
                    }
                }
            }
        }
    }

    /**
     * Decodes (reconstructs) missing shards.
     * <p>
     * Shards that are present should be non-null. Missing shards should be null in
     * the array
     * OR `shardPresent` boolean array should indicate status.
     * Here we adopt: `shards` contains buffers for ALL shards. `shardPresent[i]`
     * tells if it's valid.
     * Invalid shards in `shards` will be overwritten with reconstructed data.
     */
    public void decodeMissing(byte[][] shards, boolean[] shardPresent, int shardSize) {
        checkShards(shards);
        if (shardPresent.length != totalShards) {
            throw new IllegalArgumentException("shardPresent length must match totalShards");
        }

        int numberPresent = 0;
        for (boolean p : shardPresent) {
            if (p)
                numberPresent++;
        }

        if (numberPresent < dataShards) {
            throw new IllegalArgumentException(
                    "Not enough shards present to recover data. Need " + dataShards + ", have " + numberPresent);
        }

        // Identify which shards are unknown
        // If all data shards are present, we just need to recompute parity (if any
        // parity is missing)
        // But usually we care about recovering data shards.

        // This is a simplified "Erasure Code" view:
        // Equation: [Encoding Matrix] * [Data] = [Parity]
        // But actually the full system is: [Identity | Encoding Matrix] * [Data] =
        // [Data | Parity]
        // So we satisfy Y = A * X where X is data vector.
        // We have a subset of Y values (some data, some parity). We want to solve for
        // X.

        // If we implement proper matrix inversion for the submatrix corresponding to
        // available shards, we can solve X.

        // 1. Construct the submatrix of the VanderMonde matrix corresponding to the
        // *available* shards.
        // The top part of full generator matrix is Identity (for data shards), bottom
        // is Parity Gen Matrix.

        // Available rows indices
        int[] availableIndices = new int[dataShards]; // We pick exactly k available shards to solve
        int found = 0;

        // Prefer data shards if available (Identity rows are easy)
        for (int i = 0; i < dataShards; i++) {
            if (shardPresent[i]) {
                availableIndices[found++] = i;
            }
        }

        // Fill rest with available parity shards if needed
        for (int i = dataShards; i < totalShards && found < dataShards; i++) {
            if (shardPresent[i]) {
                availableIndices[found++] = i;
            }
        }

        // Build the submatrix composed of these rows from the full generator matrix
        byte[][] subMatrix = new byte[dataShards][dataShards];
        for (int r = 0; r < dataShards; r++) {
            int shardIdx = availableIndices[r];
            if (shardIdx < dataShards) {
                // Identity matrix row
                subMatrix[r][shardIdx] = 1;
            } else {
                // Parity matrix row
                System.arraycopy(encodingMatrix[shardIdx - dataShards], 0, subMatrix[r], 0, dataShards);
            }
        }

        // Invert this submatrix
        byte[][] invertedMatrix = invertMatrix(subMatrix);

        // Now we can recover Data Vector X = Inverted * Y_available
        // Reconstruct DATA shards first
        for (int i = 0; i < dataShards; i++) {
            if (!shardPresent[i]) {
                // Reconstruct data shard i
                // Row i of invertedMatrix * Y_available vector
                byte[] outputShard = shards[i];
                Arrays.fill(outputShard, 0, shardSize, (byte) 0);

                for (int j = 0; j < dataShards; j++) {
                    int availableShardIdx = availableIndices[j];
                    byte[] inputShard = shards[availableShardIdx];
                    int factor = invertedMatrix[i][j] & 0xFF;

                    if (factor == 0)
                        continue;

                    for (int b = 0; b < shardSize; b++) {
                        if (factor == 1) {
                            outputShard[b] ^= inputShard[b];
                        } else {
                            outputShard[b] = (byte) (outputShard[b] ^ gf.mul(inputShard[b], factor));
                        }
                    }
                }
                shardPresent[i] = true; // Now it's present
            }
        }

        // Now that we have all data shards, we can recompute any missing PARITY shards
        // easily by simple encoding
        boolean anyParityMissing = false;
        for (int i = dataShards; i < totalShards; i++) {
            if (!shardPresent[i]) {
                anyParityMissing = true;
                break;
            }
        }

        if (anyParityMissing) {
            // Re-encode parity
            // This is inefficient if we only miss one parity shard, but simple code-wise.
            // We could optimize to only compute missing rows.
            encodeParity(shards, shardSize);
        }
    }

    // --- Private Helpers ---

    private void checkShards(byte[][] shards) {
        if (shards.length != totalShards) {
            throw new IllegalArgumentException("Invalid number of shards");
        }
    }

    /**
     * Builds the encoding matrix (VanderMonde-based).
     * Actually, simple VanderMonde is not guaranteed to be invertible for any
     * submatrix.
     * We need a Cauchy matrix or a systematic VanderMonde transformed matrix.
     * 
     * A common robust approach for RS is:
     * Generator Matrix G = [ I | P ]
     * where P is constructed such that any k columns of G are linearly independent.
     * 
     * We can use a Cauchy matrix for P directly.
     * A_ij = 1 / (x_i + y_j)
     * 
     * Or use standard VanderMonde and Gaussian Elimination to convert top k rows to
     * Identity.
     * Let's use simple VanderMonde then Gaussian elimination to make it systematic.
     */
    private byte[][] buildEncodingMatrix(int k, int m) {
        // Create standard VanderMonde matrix of size (k+m) x k
        // Row i: x_i^0, x_i^1, ... x_i^{k-1}
        // distinct x_i

        byte[][] vm = new byte[k + m][k];
        for (int r = 0; r < k + m; r++) {
            for (int c = 0; c < k; c++) {
                vm[r][c] = (byte) gf.pow(r, c); // Or use other distinct generators
            }
        }
        // Note: row 0 is all 0^c -> 0^0=1, 0^1=0... -> 1, 0, 0...
        // row 1 is all 1s

        // We want the top k rows to be Identity.
        // Currently they are VanderMonde submatrix.
        // Let top submatrix be V_k.
        // We want G' = V_k^{-1} * G.
        // Then top part will be V_k^{-1} * V_k = I.
        // Bottom part will be V_k^{-1} * V_m.

        byte[][] topPart = new byte[k][k];
        for (int i = 0; i < k; i++) {
            System.arraycopy(vm[i], 0, topPart[i], 0, k);
        }

        byte[][] invertedTop = invertMatrix(topPart);

        // Now multiply bottom part (m rows) by invertedTop
        byte[][] parityMatrix = new byte[m][k];

        for (int r = 0; r < m; r++) { // destination row in parityMatrix
            int vmRowIdx = k + r; // source row in VM
            // parityRow = invertedTop * vm[vmRowIdx]
            // Actually we need matrix multiplication: P = V_bottom * V_top^{-1}?
            // Wait, the transformation applies to the whole matrix.
            // G_new = G_old * V_top^{-1} (Column operations? No.)
            // We want linear combinations of rows? No, the code is C = D * G.
            // If we change basis of Data, we change G.
            // Code words are linear combinations of basis vectors.
            // Correct approach: G is (k+m) x k.
            // We want to transform G via row operations? No, that changes the code space?
            // Yes.
            // But RS codes are MDS, so we just need ANY matrix where every square submatrix
            // is non-singular.
            // Taking a VanderMonde and multiplying by inverse of top part is effectively
            // changing the basis of the input data to match the first k code symbols.
            // Yes, G' = G * V_top^{-1}.
            // Then top k rows of G' are V_top * V_top^{-1} = I.
            // Bottom m rows are V_bottom * V_top^{-1}.

            // So we compute P = V_bottom * V_top^{-1}.

            for (int c = 0; c < k; c++) { // column in parityMatrix
                int val = 0;
                for (int col = 0; col < k; col++) {
                    // Dot product of (VM row (k+r)) and (InvertedTop col c)
                    // But InvertedTop is k x k.
                    // Matrix Mul: (m x k) * (k x k) -> (m x k)
                    int a = vm[k + r][col] & 0xFF;
                    int b = invertedTop[col][c] & 0xFF;
                    val ^= gf.mul(a, b);
                }
                parityMatrix[r][c] = (byte) val;
            }
        }

        return parityMatrix;
    }

    /**
     * Matrix Inversion using Gauss-Jordan.
     */
    private byte[][] invertMatrix(byte[][] matrix) {
        int n = matrix.length;
        // Create augmented matrix [A | I]
        byte[][] augmented = new byte[n][2 * n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(matrix[i], 0, augmented[i], 0, n);
            augmented[i][n + i] = 1;
        }

        // Gaussian elimination
        for (int i = 0; i < n; i++) {
            // Find pivot
            int pivotRow = i;
            while (pivotRow < n && augmented[pivotRow][i] == 0) {
                pivotRow++;
            }
            if (pivotRow == n) {
                throw new ArithmeticException("Matrix is singular");
            }

            // Swap rows
            byte[] temp = augmented[i];
            augmented[i] = augmented[pivotRow];
            augmented[pivotRow] = temp;

            // Scale pivot row to 1
            int pivotVal = augmented[i][i] & 0xFF;
            int pivotInv = gf.inverse(pivotVal);
            for (int j = 0; j < 2 * n; j++) {
                augmented[i][j] = (byte) gf.mul(augmented[i][j] & 0xFF, pivotInv);
            }

            // Eliminate other rows
            for (int row = 0; row < n; row++) {
                if (row != i) {
                    int factor = augmented[row][i] & 0xFF;
                    if (factor != 0) {
                        for (int col = 0; col < 2 * n; col++) {
                            augmented[row][col] = (byte) (augmented[row][col]
                                    ^ gf.mul(augmented[i][col] & 0xFF, factor));
                        }
                    }
                }
            }
        }

        // Extract inverse
        byte[][] inverse = new byte[n][n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(augmented[i], n, inverse[i], 0, n);
        }
        return inverse;
    }
}

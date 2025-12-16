
package com.justsyncit.benchmark;

import com.justsyncit.hash.Blake3Service;
import com.justsyncit.scanner.FileChunker;
import com.justsyncit.scanner.FastCDCFileChunker;
import com.justsyncit.scanner.ChunkingOptions;
import com.justsyncit.ServiceFactory;
import org.openjdk.jmh.annotations.*;
import java.nio.file.*;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 1, warmups = 1)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
public class FastCDCBenchmark {

    private Path tempFile;
    private FileChunker cdcChunker;
    private FileChunker fixedChunker;
    private ChunkingOptions cdcOptions;
    private ChunkingOptions fixedOptions;

    @Setup
    public void setup() throws Exception {
        ServiceFactory factory = new ServiceFactory();
        Blake3Service blake3 = factory.createBlake3Service();
        cdcChunker = factory.createFastCDCFileChunker(blake3);
        fixedChunker = factory.createFileChunker(blake3, ChunkingOptions.ChunkingAlgorithm.FIXED);

        cdcOptions = new ChunkingOptions().withAlgorithm(ChunkingOptions.ChunkingAlgorithm.CDC);
        fixedOptions = new ChunkingOptions().withAlgorithm(ChunkingOptions.ChunkingAlgorithm.FIXED);

        tempFile = Files.createTempFile("benchmark", ".dat");
        byte[] data = new byte[10 * 1024 * 1024]; // 10MB
        new Random().nextBytes(data);
        Files.write(tempFile, data);
    }

    @TearDown
    public void tearDown() throws Exception {
        Files.deleteIfExists(tempFile);
        if (cdcChunker instanceof FastCDCFileChunker) {
            ((FastCDCFileChunker) cdcChunker).close();
        }
        // Fixed chunker (impl by FixedSizeFileChunker) might trigger async close but we
        // ignore here
    }

    @Benchmark
    public void fastCDC() throws Exception {
        cdcChunker.chunkFile(tempFile, cdcOptions).get();
    }

    @Benchmark
    public void fixedSize() throws Exception {
        fixedChunker.chunkFile(tempFile, fixedOptions).get();
    }
}

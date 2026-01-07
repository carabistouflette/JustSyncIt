package com.justsyncit.benchmark;

import com.justsyncit.scanner.FileChunker;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ChunkingBenchmark {

    private Path tempFile;
    private FileChunker chunker;

    @Param({ "1048576", "10485760" }) // 1MB, 10MB
    private int fileSize;

    @Setup
    public void setup() throws Exception {
        // Use ServiceFactory to get dependencies
        com.justsyncit.ServiceFactory factory = new com.justsyncit.ServiceFactory();
        com.justsyncit.hash.Blake3Service blake3Service = factory.createBlake3Service();

        // Use static factory method
        chunker = com.justsyncit.scanner.FixedSizeFileChunker.create(blake3Service);

        tempFile = Files.createTempFile("benchmark-test-", ".dat");

        byte[] bytes = new byte[fileSize];
        new Random().nextBytes(bytes);
        Files.write(tempFile, bytes);
    }

    @TearDown
    public void tearDown() throws IOException {
        Files.deleteIfExists(tempFile);
    }

    @Benchmark
    public void benchmarkChunking() throws IOException {
        // Pass default options
        com.justsyncit.scanner.ChunkingOptions options = new com.justsyncit.scanner.ChunkingOptions();
        chunker.chunkFile(tempFile, options).join(); // join() to wait for completion in benchmark
    }
}

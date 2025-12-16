
package com.justsyncit.benchmark;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@Tag("benchmark")
public class FastCDCJmhRunnerTest {

    @Test
    public void runJmhBenchmarks() throws Exception {
        Options opt = new OptionsBuilder()
                .include(FastCDCBenchmark.class.getSimpleName())
                .resultFormat(ResultFormatType.TEXT)
                .output("benchmark_results.txt")
                .build();

        new Runner(opt).run();
    }
}

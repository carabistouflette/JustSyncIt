package com.justsyncit.performance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.TimeUnit;

/**
 * Test class for the AsyncVsSyncBenchmarkSuite.
 * This test validates that the benchmark suite can execute successfully
 * and generate comprehensive performance reports.
 */
@Tag("performance")
@Tag("benchmark")
public class AsyncVsSyncBenchmarkSuiteTest {

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    @DisplayName("Test complete benchmark suite execution")
    public void testCompleteBenchmarkSuite() throws Exception {
        AsyncVsSyncBenchmarkSuite suite = new AsyncVsSyncBenchmarkSuite();
        
        // Execute the complete benchmark suite using reflection since the main method is private
        try {
            // Use reflection to call the main test method
            java.lang.reflect.Method setUpMethod = AsyncVsSyncBenchmarkSuite.class.getDeclaredMethod("setUp");
            setUpMethod.setAccessible(true);
            setUpMethod.invoke(suite);
            
            java.lang.reflect.Method runMethod = AsyncVsSyncBenchmarkSuite.class.getDeclaredMethod("runComprehensiveBenchmarkSuite");
            runMethod.setAccessible(true);
            runMethod.invoke(suite);
            
            java.lang.reflect.Method tearDownMethod = AsyncVsSyncBenchmarkSuite.class.getDeclaredMethod("tearDown");
            tearDownMethod.setAccessible(true);
            tearDownMethod.invoke(suite);
            
            System.out.println("Benchmark suite executed successfully!");
            
        } catch (Exception e) {
            fail("Benchmark suite execution failed: " + e.getMessage());
        }
    }
    
    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    @DisplayName("Test core performance benchmarks only")
    public void testCorePerformanceBenchmarks() throws Exception {
        AsyncVsSyncBenchmarkSuite suite = new AsyncVsSyncBenchmarkSuite();
        
        try {
            // Use reflection to call setUp
            java.lang.reflect.Method setUpMethod = AsyncVsSyncBenchmarkSuite.class.getDeclaredMethod("setUp");
            setUpMethod.setAccessible(true);
            setUpMethod.invoke(suite);
            
            // Use reflection to call only core performance benchmarks
            java.lang.reflect.Method coreMethod = AsyncVsSyncBenchmarkSuite.class.getDeclaredMethod("runCorePerformanceBenchmarks");
            coreMethod.setAccessible(true);
            coreMethod.invoke(suite);
            
            // Use reflection to call tearDown
            java.lang.reflect.Method tearDownMethod = AsyncVsSyncBenchmarkSuite.class.getDeclaredMethod("tearDown");
            tearDownMethod.setAccessible(true);
            tearDownMethod.invoke(suite);
            
            System.out.println("Core performance benchmarks executed successfully!");
            
        } catch (Exception e) {
            System.out.println("Exception occurred: " + e.getClass().getName());
            System.out.println("Exception message: " + e.getMessage());
            if (e.getCause() != null) {
                System.out.println("Root cause: " + e.getCause().getClass().getName());
                System.out.println("Root cause message: " + e.getCause().getMessage());
            }
            e.printStackTrace();
            fail("Core performance benchmarks execution failed: " + e.getMessage());
        }
    }
    
    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    @DisplayName("Test performance target validation")
    public void testPerformanceTargetValidation() throws Exception {
        AsyncVsSyncBenchmarkSuite suite = new AsyncVsSyncBenchmarkSuite();
        
        try {
            // Use reflection to call setUp
            java.lang.reflect.Method setUpMethod = AsyncVsSyncBenchmarkSuite.class.getDeclaredMethod("setUp");
            setUpMethod.setAccessible(true);
            setUpMethod.invoke(suite);
            
            // Use reflection to call core benchmarks first
            java.lang.reflect.Method coreMethod = AsyncVsSyncBenchmarkSuite.class.getDeclaredMethod("runCorePerformanceBenchmarks");
            coreMethod.setAccessible(true);
            coreMethod.invoke(suite);
            
            // Use reflection to call performance target validation
            java.lang.reflect.Method validateMethod = AsyncVsSyncBenchmarkSuite.class.getDeclaredMethod("validatePerformanceTargets");
            validateMethod.setAccessible(true);
            validateMethod.invoke(suite);
            
            // Use reflection to call tearDown
            java.lang.reflect.Method tearDownMethod = AsyncVsSyncBenchmarkSuite.class.getDeclaredMethod("tearDown");
            tearDownMethod.setAccessible(true);
            tearDownMethod.invoke(suite);
            
            System.out.println("Performance target validation completed successfully!");
            
        } catch (Exception e) {
            fail("Performance target validation failed: " + e.getMessage());
        }
    }
}
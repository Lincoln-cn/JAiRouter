package org.unreal.modelrouter.benchmark;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

/**
 * JMH 基准测试运行器
 *
 * 运行方式：
 * 1. Maven: mvn test -Dtest=BenchmarkRunner
 * 2. 直接运行: java -jar target/benchmarks.jar
 *
 * @author JAiRouter Team
 * @since v2.7.13
 */
public class BenchmarkRunner {

    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder()
                .include(PerformanceBenchmark.class.getSimpleName())
                // 快速模式：减少迭代次数用于开发调试
                .warmupIterations(2)
                .warmupTime(TimeValue.seconds(1))
                .measurementIterations(3)
                .measurementTime(TimeValue.seconds(1))
                .forks(1)
                // 输出格式
                .shouldFailOnError(true)
                .shouldDoGC(true)
                .build();

        new Runner(options).run();
    }

    /**
     * 完整测试模式（用于正式验收）
     */
    public static void runFullBenchmark() throws RunnerException {
        Options options = new OptionsBuilder()
                .include(PerformanceBenchmark.class.getSimpleName())
                // 完整模式：更多迭代获得更准确结果
                .warmupIterations(5)
                .warmupTime(TimeValue.seconds(2))
                .measurementIterations(10)
                .measurementTime(TimeValue.seconds(2))
                .forks(3)
                // 输出报告
                .shouldFailOnError(true)
                .shouldDoGC(true)
                .jvmArgs("-Xms1G", "-Xmx1G")
                .build();

        new Runner(options).run();
    }
}

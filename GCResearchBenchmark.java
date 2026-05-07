import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GCResearchBenchmark {

    private static final int WARMUP_ROUNDS = 5;
    private static final int MEASURED_ROUNDS = 15;

    private static final int OUTER_LOOPS = 120;
    private static final int INNER_ALLOCATIONS = 80;
    private static final int BLOCK_SIZE_BYTES = 256 * 1024;
    private static final int RETAIN_EVERY = 8;

    private static volatile long blackhole = 0;

    public static void main(String[] args) {
        Runtime runtime = Runtime.getRuntime();

        System.out.println("==============================================");
        System.out.println("GC Research Benchmark");
        System.out.println("Warm-up Rounds   : " + WARMUP_ROUNDS);
        System.out.println("Measured Rounds  : " + MEASURED_ROUNDS);
        System.out.println("Outer Loops      : " + OUTER_LOOPS);
        System.out.println("Inner Allocations: " + INNER_ALLOCATIONS);
        System.out.println("Block Size       : " + (BLOCK_SIZE_BYTES / 1024) + " KB");
        System.out.println("==============================================");

        for (int i = 1; i <= WARMUP_ROUNDS; i++) {
            runWorkload();
            sleep(150);
            System.out.println("Warm-up Round " + i + " completed");
        }

        List<Result> results = new ArrayList<>();

        for (int round = 1; round <= MEASURED_ROUNDS; round++) {
            sleep(120);

            long memBefore = usedMemory(runtime);
            GCStats gcBefore = captureGCStats();
            long cpuBeforeNs = getProcessCpuTimeNs();

            long startNs = System.nanoTime();
            long checksum = runWorkload();
            long endNs = System.nanoTime();

            long cpuAfterNs = getProcessCpuTimeNs();
            GCStats gcAfter = captureGCStats();
            long memAfter = usedMemory(runtime);

            long execTimeMs = (endNs - startNs) / 1_000_000;
            long gcCountDelta = gcAfter.count - gcBefore.count;
            long gcTimeDeltaMs = gcAfter.timeMs - gcBefore.timeMs;
            long cpuTimeDeltaNs = cpuAfterNs - cpuBeforeNs;

            double avgPauseMs = gcCountDelta > 0 ? (double) gcTimeDeltaMs / gcCountDelta : 0.0;
            double throughput = execTimeMs > 0
                    ? ((execTimeMs - gcTimeDeltaMs) / (double) execTimeMs) * 100.0
                    : 0.0;

            double cpuUsage = computeCpuUsagePercent(cpuTimeDeltaNs, endNs - startNs);

            Result r = new Result(
                    round,
                    execTimeMs,
                    memBefore,
                    memAfter,
                    gcCountDelta,
                    gcTimeDeltaMs,
                    avgPauseMs,
                    throughput,
                    cpuUsage,
                    checksum
            );

            results.add(r);

            System.out.println("----------------------------------------------");
            System.out.println("Round           : " + round);
            System.out.println("Execution Time  : " + execTimeMs + " ms");
            System.out.println("Avg Pause Time  : " + format(avgPauseMs) + " ms");
            System.out.println("Throughput      : " + format(throughput) + " %");
            System.out.println("CPU Usage       : " + format(cpuUsage) + " %");
            System.out.println("GC Count Delta  : " + gcCountDelta);
            System.out.println("GC Time Delta   : " + gcTimeDeltaMs + " ms");
            System.out.println("Memory Before   : " + format(bytesToMB(memBefore)) + " MB");
            System.out.println("Memory After    : " + format(bytesToMB(memAfter)) + " MB");
            System.out.println("Checksum        : " + checksum);
        }

        printSummary(results);
        saveCsv(results, "gc_research_results.csv");

        System.out.println("==============================================");
        System.out.println("CSV Saved       : gc_research_results.csv");
        System.out.println("Blackhole       : " + blackhole);
        System.out.println("==============================================");
    }

    private static long runWorkload() {
        List<byte[]> retained = new ArrayList<>();
        long checksum = 0;

        for (int outer = 1; outer <= OUTER_LOOPS; outer++) {
            List<byte[]> temp = new ArrayList<>();

            for (int inner = 1; inner <= INNER_ALLOCATIONS; inner++) {
                byte[] block = new byte[BLOCK_SIZE_BYTES];

                block[0] = (byte) (outer + inner);
                block[BLOCK_SIZE_BYTES - 1] = (byte) (outer * inner);

                checksum += block[0];
                checksum += block[BLOCK_SIZE_BYTES - 1];

                temp.add(block);

                if (inner % RETAIN_EVERY == 0) {
                    retained.add(block);
                }
            }

            if (retained.size() > 200) {
                retained = new ArrayList<>(retained.subList(retained.size() / 2, retained.size()));
            }
        }

        blackhole ^= checksum;
        return checksum;
    }

    private static GCStats captureGCStats() {
        long totalCount = 0;
        long totalTimeMs = 0;

        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long c = bean.getCollectionCount();
            long t = bean.getCollectionTime();

            if (c != -1) {
                totalCount += c;
            }

            if (t != -1) {
                totalTimeMs += t;
            }
        }

        return new GCStats(totalCount, totalTimeMs);
    }

    private static long getProcessCpuTimeNs() {
        try {
            com.sun.management.OperatingSystemMXBean osBean =
                    (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            return osBean.getProcessCpuTime();
        } catch (Exception e) {
            return 0;
        }
    }

    private static double computeCpuUsagePercent(long cpuTimeDeltaNs, long wallTimeDeltaNs) {
        if (cpuTimeDeltaNs <= 0 || wallTimeDeltaNs <= 0) {
            return 0.0;
        }

        int cores = Runtime.getRuntime().availableProcessors();
        double usage = (cpuTimeDeltaNs / (double) (wallTimeDeltaNs * cores)) * 100.0;

        if (usage < 0) {
            return 0.0;
        }

        if (usage > 100.0) {
            return 100.0;
        }

        return usage;
    }

    private static long usedMemory(Runtime runtime) {
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static double bytesToMB(long bytes) {
        return bytes / (1024.0 * 1024.0);
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static void saveCsv(List<Result> results, String fileName) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(fileName))) {
            pw.println("Round,ExecutionTimeMs,AvgPauseMs,ThroughputPercent,CpuUsagePercent,GCCountDelta,GCTimeDeltaMs,MemoryBeforeMB,MemoryAfterMB,Checksum");

            for (Result r : results) {
                pw.printf(
                        Locale.US,
                        "%d,%d,%.2f,%.2f,%.2f,%d,%d,%.2f,%.2f,%d%n",
                        r.round,
                        r.execTimeMs,
                        r.avgPauseMs,
                        r.throughput,
                        r.cpuUsage,
                        r.gcCount,
                        r.gcTimeMs,
                        bytesToMB(r.memBefore),
                        bytesToMB(r.memAfter),
                        r.checksum
                );
            }
        } catch (IOException e) {
            System.out.println("CSV write error: " + e.getMessage());
        }
    }

    private static void printSummary(List<Result> results) {
        double totalExec = 0;
        double totalPause = 0;
        double totalThroughput = 0;
        double totalCpu = 0;
        double totalMemBefore = 0;
        double totalMemAfter = 0;
        double totalGcTime = 0;
        double totalGcCount = 0;

        long minExec = Long.MAX_VALUE;
        long maxExec = Long.MIN_VALUE;

        for (Result r : results) {
            totalExec += r.execTimeMs;
            totalPause += r.avgPauseMs;
            totalThroughput += r.throughput;
            totalCpu += r.cpuUsage;
            totalMemBefore += bytesToMB(r.memBefore);
            totalMemAfter += bytesToMB(r.memAfter);
            totalGcTime += r.gcTimeMs;
            totalGcCount += r.gcCount;

            if (r.execTimeMs < minExec) {
                minExec = r.execTimeMs;
            }

            if (r.execTimeMs > maxExec) {
                maxExec = r.execTimeMs;
            }
        }

        int n = results.size();

        System.out.println("==============================================");
        System.out.println("FINAL SUMMARY");
        System.out.println("Average Exec Time : " + format(totalExec / n) + " ms");
        System.out.println("Minimum Exec Time : " + minExec + " ms");
        System.out.println("Maximum Exec Time : " + maxExec + " ms");
        System.out.println("Avg Pause Time    : " + format(totalPause / n) + " ms");
        System.out.println("Throughput        : " + format(totalThroughput / n) + " %");
        System.out.println("CPU Usage         : " + format(totalCpu / n) + " %");
        System.out.println("Avg GC Time       : " + format(totalGcTime / n) + " ms");
        System.out.println("Avg GC Count      : " + format(totalGcCount / n));
        System.out.println("Avg Mem Before    : " + format(totalMemBefore / n) + " MB");
        System.out.println("Avg Mem After     : " + format(totalMemAfter / n) + " MB");
        System.out.println("==============================================");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static class GCStats {
        long count;
        long timeMs;

        GCStats(long count, long timeMs) {
            this.count = count;
            this.timeMs = timeMs;
        }
    }

    static class Result {
        int round;
        long execTimeMs;
        long memBefore;
        long memAfter;
        long gcCount;
        long gcTimeMs;
        double avgPauseMs;
        double throughput;
        double cpuUsage;
        long checksum;

        Result(int round, long execTimeMs, long memBefore, long memAfter,
               long gcCount, long gcTimeMs, double avgPauseMs,
               double throughput, double cpuUsage, long checksum) {
            this.round = round;
            this.execTimeMs = execTimeMs;
            this.memBefore = memBefore;
            this.memAfter = memAfter;
            this.gcCount = gcCount;
            this.gcTimeMs = gcTimeMs;
            this.avgPauseMs = avgPauseMs;
            this.throughput = throughput;
            this.cpuUsage = cpuUsage;
            this.checksum = checksum;
        }
    }
}
# 2210992102_2136_2447_296

Roll Numbers & Names:
Priyanshi Jain      2210992102
Rakshita            2210992136
Taniya              2210992447
Divyanjali Singla  2210990296

PROJECT TITLE: 
Performance Analysis of JVM Garbage Collectors under Memory-Intensive Workloads

TYPE: 
Research

CURRENT STATUS: 
Submitted


Commands to run the code:
For Serial GC run:
java -Xms2g -Xmx2g -XX:+UseSerialGC GCResearchBenchmark

For Parallel GC run:
java -Xms2g -Xmx2g -XX:+UseParallelGC GCResearchBenchmark

For G1 GC run:
java -Xms2g -Xmx2g -XX:+UseG1GC GCResearchBenchmark

For ZGC run:
java -Xms2g -Xmx2g -XX:+UseZGC GCResearchBenchmark

For Shenandoah GC run:
java -Xms2g -Xmx2g -XX:+UseShenandoahGC GCResearchBenchmark

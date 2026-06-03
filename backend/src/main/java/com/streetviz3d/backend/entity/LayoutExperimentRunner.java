package com.streetviz3d.backend.entity;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

public class LayoutExperimentRunner {

    private static final double ROAD_LENGTH = 80.0;
    private static final double EDGE_PADDING = 2.0;
    private static final int REPEAT_TIMES = 100;

    /**
     * 这里使用 40 * 120 = 4800 次评估。
     * RS 为公平起见，也采样 4800 次。
     */
    private static final int GA_POPULATION_SIZE = 40;
    private static final int GA_GENERATIONS = 120;
    private static final int RS_SAMPLE_COUNT = GA_POPULATION_SIZE * GA_GENERATIONS;

    public static void main(String[] args) throws Exception {
        runExperiment(4);
        runExperiment(8);
        runExperiment(12);
        runExperiment(14);
    }

    private static void runExperiment(int count) throws Exception {
        double[] modelDepths = new double[count];
        Arrays.fill(modelDepths, 5.0);

        ExperimentRecord[] rsRecords = new ExperimentRecord[REPEAT_TIMES];
        ExperimentRecord[] gaRecords = new ExperimentRecord[REPEAT_TIMES];

        for (int i = 0; i < REPEAT_TIMES; i++) {
            rsRecords[i] = runRandomSearch(count, modelDepths);
            gaRecords[i] = runGA(count, modelDepths);
        }

        Summary rsSummary = summarize("RS", count, rsRecords);
        Summary gaSummary = summarize("GA", count, gaRecords);

        writeSummaryToCsv("layout_experiment_count_" + count + ".csv", rsSummary, gaSummary);

        System.out.println("实例数 n = " + count);
        System.out.println(rsSummary);
        System.out.println(gaSummary);
    }

    private static ExperimentRecord runRandomSearch(int count, double[] modelDepths) {
        long start = System.nanoTime();

        double[] z = LayoutGA.optimizeZPositionsRandomSearch(
                count,
                ROAD_LENGTH,
                EDGE_PADDING,
                modelDepths,
                RS_SAMPLE_COUNT
        );

        long end = System.nanoTime();

        double fitness = LayoutGA.fitness(z, ROAD_LENGTH, EDGE_PADDING, modelDepths);
        double overlapPenalty = calculateOverlapPenalty(z, modelDepths);
        double elapsedMs = (end - start) / 1_000_000.0;

        return new ExperimentRecord(fitness, overlapPenalty, elapsedMs);
    }

    private static ExperimentRecord runGA(int count, double[] modelDepths) throws IOException {
        long start = System.nanoTime();

        double[] z = LayoutGA.optimizeZPositionsGA(
                count,
                ROAD_LENGTH,
                EDGE_PADDING,
                modelDepths,
                GA_GENERATIONS,
                GA_POPULATION_SIZE
        );

        long end = System.nanoTime();

        double fitness = LayoutGA.fitness(z, ROAD_LENGTH, EDGE_PADDING, modelDepths);
        double overlapPenalty = calculateOverlapPenalty(z, modelDepths);
        double elapsedMs = (end - start) / 1_000_000.0;

        return new ExperimentRecord(fitness, overlapPenalty, elapsedMs);
    }

    /**
     * 计算重叠惩罚：
     * P_s(z) = sum max(0, (d_i+d_{i+1})/2 - Δ_i)
     */
    private static double calculateOverlapPenalty(double[] z, double[] depths) {
        double penalty = 0.0;

        for (int i = 0; i < z.length - 1; i++) {
            double actualGap = z[i + 1] - z[i];
            double minGap = depths[i] / 2.0 + depths[i + 1] / 2.0;

            if (actualGap < minGap) {
                penalty += minGap - actualGap;
            }
        }

        return penalty;
    }

    private static Summary summarize(String algorithm, int count, ExperimentRecord[] records) {
        double bestFitness = Arrays.stream(records)
                .mapToDouble(r -> r.fitness)
                .max()
                .orElse(0.0);

        double avgFitness = Arrays.stream(records)
                .mapToDouble(r -> r.fitness)
                .average()
                .orElse(0.0);

        double stdFitness = std(
                Arrays.stream(records).mapToDouble(r -> r.fitness).toArray(),
                avgFitness
        );

        double avgOverlapPenalty = Arrays.stream(records)
                .mapToDouble(r -> r.overlapPenalty)
                .average()
                .orElse(0.0);

        double avgTimeMs = Arrays.stream(records)
                .mapToDouble(r -> r.elapsedMs)
                .average()
                .orElse(0.0);

        return new Summary(
                count,
                algorithm,
                bestFitness,
                avgFitness,
                stdFitness,
                avgOverlapPenalty,
                avgTimeMs
        );
    }

    private static double std(double[] values, double mean) {
        double variance = Arrays.stream(values)
                .map(v -> (v - mean) * (v - mean))
                .average()
                .orElse(0.0);

        return Math.sqrt(variance);
    }

    private static void writeSummaryToCsv(String fileName, Summary... summaries) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            writer.write("count,algorithm,bestFitness,avgFitness,stdFitness,avgOverlapPenalty,avgTimeMs");
            writer.newLine();

            for (Summary s : summaries) {
                writer.write(s.count + ","
                        + s.algorithm + ","
                        + s.bestFitness + ","
                        + s.avgFitness + ","
                        + s.stdFitness + ","
                        + s.avgOverlapPenalty + ","
                        + s.avgTimeMs);
                writer.newLine();
            }
        }
    }

    private static class ExperimentRecord {
        double fitness;
        double overlapPenalty;
        double elapsedMs;

        ExperimentRecord(double fitness, double overlapPenalty, double elapsedMs) {
            this.fitness = fitness;
            this.overlapPenalty = overlapPenalty;
            this.elapsedMs = elapsedMs;
        }
    }

    private static class Summary {
        int count;
        String algorithm;
        double bestFitness;
        double avgFitness;
        double stdFitness;
        double avgOverlapPenalty;
        double avgTimeMs;

        Summary(
                int count,
                String algorithm,
                double bestFitness,
                double avgFitness,
                double stdFitness,
                double avgOverlapPenalty,
                double avgTimeMs
        ) {
            this.count = count;
            this.algorithm = algorithm;
            this.bestFitness = bestFitness;
            this.avgFitness = avgFitness;
            this.stdFitness = stdFitness;
            this.avgOverlapPenalty = avgOverlapPenalty;
            this.avgTimeMs = avgTimeMs;
        }

        @Override
        public String toString() {
            return "Summary{" +
                    "count=" + count +
                    ", algorithm='" + algorithm + '\'' +
                    ", bestFitness=" + bestFitness +
                    ", avgFitness=" + avgFitness +
                    ", stdFitness=" + stdFitness +
                    ", avgOverlapPenalty=" + avgOverlapPenalty +
                    ", avgTimeMs=" + avgTimeMs +
                    '}';
        }
    }
}
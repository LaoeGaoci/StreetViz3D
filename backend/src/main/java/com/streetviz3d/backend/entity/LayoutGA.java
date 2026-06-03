package com.streetviz3d.backend.entity;

import java.io.*;
import java.util.*;
import java.util.stream.DoubleStream;

public class LayoutGA {
    private static final Random RANDOM = new Random();

    public static double[] optimizeZPositionsGA(
            int count,
            double roadLength,
            double edgePadding,
            double[] modelDepths,
            int generations,
            int populationSize
    ) throws IOException {
        if (count <= 1) return new double[]{0.0};
        if (modelDepths.length != count) {
            throw new IllegalArgumentException("modelDepths length must equal count");
        }

        List<double[]> population = new ArrayList<>();
        for (int i = 0; i < populationSize; i++) {
            double[] individual = new double[count];
            double usableLength = roadLength - 2 * edgePadding;
            for (int j = 0; j < count; j++) {
                individual[j] = edgePadding + RANDOM.nextDouble() * usableLength - roadLength / 2.0;
            }
            Arrays.sort(individual);
            population.add(individual);
        }

        // 记录每代最大适应度
        List<Double> maxFitnessPerGen = new ArrayList<>();

        for (int gen = 0; gen < generations; gen++) {
            Map<double[], Double> fitnessMap = new HashMap<>();
            for (double[] individual : population) {
                fitnessMap.put(individual, fitness(individual, roadLength, edgePadding, modelDepths));
            }

            // 记录本代最大适应度
            double maxFit = fitnessMap.values().stream().mapToDouble(d -> d).max().orElse(0.0);
            maxFitnessPerGen.add(maxFit);

            // 选择前50%
            List<double[]> sorted = population.stream()
                    .sorted((a, b) -> Double.compare(fitnessMap.get(b), fitnessMap.get(a)))
                    .toList();
            population = new ArrayList<>(sorted.subList(0, populationSize / 2));

            // 交叉 + 变异生成新个体
            while (population.size() < populationSize) {
                double[] parent1 = population.get(RANDOM.nextInt(population.size()));
                double[] parent2 = population.get(RANDOM.nextInt(population.size()));
                double[] child = crossover(parent1, parent2);
                mutate(child, roadLength, edgePadding, 0.05);
                Arrays.sort(child);
                population.add(child);
            }
        }

//        String fileName = "fitness_curve_" + System.currentTimeMillis() + ".csv";
//        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
//            for (int i = 0; i < maxFitnessPerGen.size(); i++) {
//                writer.write((i+1) + "," + maxFitnessPerGen.get(i));
//                writer.newLine();
//            }
//        } catch (IOException e) {
//            System.err.println("写 CSV 出错: " + e.getMessage());
//        }

        // 返回适应度最高个体
        return population.stream()
                .max(Comparator.comparingDouble(ind -> fitness(ind, roadLength, edgePadding, modelDepths)))
                .orElse(population.get(0));
    }

    public static double[] optimizeZPositionsRandomSearch(
            int count,
            double roadLength,
            double edgePadding,
            double[] modelDepths,
            int sampleCount
    ) {
        if (count <= 1) return new double[]{0.0};
        if (modelDepths.length != count) {
            throw new IllegalArgumentException("modelDepths length must equal count");
        }

        double[] bestIndividual = null;
        double bestFitness = Double.NEGATIVE_INFINITY;
        double usableLength = roadLength - 2 * edgePadding;

        for (int i = 0; i < sampleCount; i++) {
            double[] individual = new double[count];

            for (int j = 0; j < count; j++) {
                individual[j] = edgePadding
                        + RANDOM.nextDouble() * usableLength
                        - roadLength / 2.0;
            }

            Arrays.sort(individual);

            double currentFitness = fitness(individual, roadLength, edgePadding, modelDepths);

            if (currentFitness > bestFitness) {
                bestFitness = currentFitness;
                bestIndividual = individual;
            }
        }

        return bestIndividual;
    }

    public static double fitness(double[] z, double roadLength, double edgePadding, double[] depths) {
        double score = 0.0;
        int n = z.length;

        double[] diffs = new double[n - 1];
        for (int i = 0; i < n - 1; i++) diffs[i] = z[i + 1] - z[i];
        double mean = DoubleStream.of(diffs).average().orElse(1.0);
        double variance = DoubleStream.of(diffs).map(d -> (d - mean) * (d - mean)).average().orElse(0.0);
        score += 1.0 / (1.0 + variance);

        for (int i = 0; i < n - 1; i++) {
            double halfLeft = depths[i] / 2.0;
            double halfRight = depths[i + 1] / 2.0;
            double minGap = halfLeft + halfRight;
            double actualGap = diffs[i];
            if (actualGap < minGap) {
                score -= (minGap - actualGap) * 5.0;
            }
        }

        double roadHalf = roadLength / 2.0;
        for (int i = 0; i < n; i++) {
            double half = depths[i] / 2.0;
            double leftEdge = z[i] - half;
            double rightEdge = z[i] + half;
            if (leftEdge < -roadHalf + edgePadding) score -= (-roadHalf + edgePadding - leftEdge) * 2.0;
            if (rightEdge > roadHalf - edgePadding) score -= (rightEdge - (roadHalf - edgePadding)) * 2.0;
        }

        return score;
    }

    private static double[] crossover(double[] parent1, double[] parent2) {
        int n = parent1.length;
        double[] child = new double[n];
        int point = RANDOM.nextInt(n);
        for (int i = 0; i < n; i++) child[i] = i < point ? parent1[i] : parent2[i];
        return child;
    }

    private static void mutate(double[] individual, double roadLength, double edgePadding, double mutationRate) {
        double usableLength = roadLength - 2 * edgePadding;
        for (int i = 0; i < individual.length; i++) {
            if (RANDOM.nextDouble() < mutationRate) {
                individual[i] += (RANDOM.nextDouble() - 0.5) * usableLength * 0.1;
            }
        }
    }
}
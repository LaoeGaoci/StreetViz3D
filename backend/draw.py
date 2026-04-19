# plot_fitness_curves_separate.py

import matplotlib.pyplot as plt
import csv
import glob
import os

# 获取当前目录下所有 fitness_curve CSV 文件
csv_files = glob.glob("fitness_curve_*.csv")
if not csv_files:
    print("未找到 fitness_curve CSV 文件，请确认文件存在")
    exit(1)

for csv_file in csv_files:
    generations = []
    max_fitness = []

    # 读取 CSV
    with open(csv_file, newline='') as f:
        reader = csv.reader(f)
        for row in reader:
            if len(row) < 2:
                continue
            try:
                generations.append(int(row[0]))
                max_fitness.append(float(row[1]))
            except ValueError:
                continue

    if not generations or not max_fitness:
        continue

    # 绘制单独图
    plt.figure(figsize=(8,5))
    plt.plot(generations, max_fitness, 'r-', marker='o', label='Max Fitness')  # 红色实线
    plt.xlabel("Generation")
    plt.ylabel("Max Fitness")
    plt.title(f"GA Z-Position Optimization: {os.path.basename(csv_file)}")
    plt.grid(True)
    plt.legend()
    plt.tight_layout()

    # 保存图像到文件
    output_name = os.path.splitext(csv_file)[0] + ".png"
    plt.savefig(output_name)
    print(f"已保存图像: {output_name}")
    plt.close()  # 关闭当前图，避免多张图叠加
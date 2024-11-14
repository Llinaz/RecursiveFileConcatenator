package main;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FileConcatenationApp {
    private static final String REQUIRE_PATTERN = "\\*require '(.+)'\\*";

    public static void main(String[] args) {
        String rootDirectory = "src/resources";
        String outputFile = "src/resources/output/result.txt";

        try {
            List<File> allFiles = new ArrayList<>();
            findAllTextFiles(Paths.get(rootDirectory), allFiles);
            Map<File, List<File>> dependencies = buildDependencies(allFiles, rootDirectory);

            List<File> sortedFiles = topologicalSort(dependencies);

            if (sortedFiles == null) {
                System.out.println("Ошибка: Обнаружена циклическая зависимость между файлами.");
            } else {
                concatenateFiles(sortedFiles, outputFile);
                System.out.println("Конкатенация завершена. Результат записан в " + outputFile);
            }
        } catch (IOException e) {
            System.err.println("Ошибка при обработке файлов: " + e.getMessage());
        }
    }

    private static void findAllTextFiles(Path rootPath, List<File> allFiles) throws IOException {
        Files.walk(rootPath)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".txt"))
                .forEach(path -> allFiles.add(path.toFile()));
    }

    private static Map<File, List<File>> buildDependencies(List<File> files, String rootDirectory) throws IOException {
        Map<File, List<File>> dependencies = new HashMap<>();
        Pattern pattern = Pattern.compile(REQUIRE_PATTERN);

        for (File file : files) {
            List<File> fileDependencies = new ArrayList<>();
            dependencies.put(file, fileDependencies);

            try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while((line = reader.readLine()) != null) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        String requiredPath = matcher.group(1);
                        File requiredFile = new File(rootDirectory, requiredPath);
                        if (files.contains(requiredFile)) {
                            fileDependencies.add(requiredFile);
                        } else {
                            System.out.println("Предупреждение: " + requiredFile + " не найден, пропущен.");
                        }
                    }
                }
            }
        }
        return dependencies;
    }

    private static List<File> topologicalSort(Map<File, List<File>> dependencies) {
        Map<File, Integer> inDegree = new HashMap<>();
        for (File file : dependencies.keySet()) {
            inDegree.put(file, 0);
        }
        for (List<File> deps : dependencies.values()) {
            for (File dep : deps) {
                inDegree.put(dep, inDegree.get(dep) + 1);
            }
        }

        Queue<File> queue = new LinkedList<>();
        for (Map.Entry<File, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<File> sorted = new ArrayList<>();
        while(!queue.isEmpty()) {
            File file = queue.poll();
            sorted.add(file);

            for (File dep : dependencies.get(file)) {
                inDegree.put(dep, inDegree.get(dep) - 1);
                if (inDegree.get(dep) == 0) {
                    queue.add(dep);
                }
            }
        }

        if (sorted.size() != dependencies.size()) {
            detectCycle(dependencies);
            return null;
        }
        return sorted;
    }

    private static void detectCycle(Map<File, List<File>> dependencies) {
        System.out.println("Циклическая зависимость обнаружена между файлами:");
        for (File file : dependencies.keySet()) {
            Set<File> visited = new HashSet<>();
            if (hasCycle(file, dependencies, visited)) {
                System.out.println("Цикл: " + visited.stream().map(File::getName).collect(Collectors.joining(" -> ")));
                return;
            }
        }
    }

    private static boolean hasCycle(File file, Map<File, List<File>> dependencies, Set<File> visited) {
        if (visited.contains(file)) {
            return true;
        }
        visited.add(file);
        for (File dep : dependencies.get(file)) {
            if (hasCycle(dep, dependencies, visited)) {
                return true;
            }
        }
        visited.remove(file);
        return false;
    }

    private static void concatenateFiles(List<File> sortedFiles, String outputFile) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            for (File file : sortedFiles) {
                Files.lines(file.toPath()).forEach(line -> {
                    try {
                        writer.write(line);
                        writer.newLine();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
                writer.newLine();
            }
        }
    }
}

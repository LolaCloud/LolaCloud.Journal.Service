package com.miranda.luna.journal.application.service;

import com.miranda.luna.journal.domain.model.ContainerStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DockerTelemetryService {

    private static final Logger logger = LoggerFactory.getLogger(DockerTelemetryService.class);
    private static final Pattern VALUE_UNIT_PATTERN = Pattern.compile("^([0-9.]+)\\s*([a-zA-Z]+)?$");

    @Value("${lola.journal.docker.enabled:true}")
    private boolean dockerEnabled;

    public boolean isDockerConnected() {
        if (!dockerEnabled) {
            return false;
        }
        CommandResult result = executeCommand(List.of("docker", "ps", "-q"), 3);
        return result.exitCode() == 0;
    }

    public List<String> listLolaContainers() {
        if (!dockerEnabled) {
            return List.of();
        }
        CommandResult result = executeCommand(List.of("docker", "ps", "--format", "{{.Names}}"), 5);
        if (result.exitCode() != 0) {
            logger.debug("Failed listing docker containers: {}", result.stderr());
            return List.of();
        }

        return Arrays.stream(result.stdout().split("\\r?\\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .filter(s -> s.startsWith("lola-") || s.contains("lola"))
                .toList();
    }

    public ContainerStats fetchContainerStats(String containerName) {
        if (!dockerEnabled || containerName == null || containerName.isBlank()) {
            return ContainerStats.defaultFor(containerName, "UNKNOWN");
        }

        List<String> command = List.of(
                "docker", "stats", "--no-stream",
                "--format", "{{.ID}}\t{{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.NetIO}}\t{{.BlockIO}}",
                containerName
        );

        CommandResult result = executeCommand(command, 6);
        if (result.exitCode() != 0 || result.stdout().isBlank()) {
            logger.debug("Failed fetching stats for {}: {}", containerName, result.stderr());
            return ContainerStats.defaultFor(containerName, "STOPPED");
        }

        String line = result.stdout().lines().findFirst().orElse("");
        String[] parts = line.split("\t");
        if (parts.length < 5) {
            return ContainerStats.defaultFor(containerName, "UNKNOWN");
        }

        try {
            String id = parts[0].trim();
            String name = parts[1].trim();
            double cpuPercent = parsePercentage(parts[2]);

            // Memory usage format: 104.9MiB / 60.39GiB
            String memUsageStr = parts[3].trim();
            long memoryUsedBytes = 0L;
            long memoryLimitBytes = 0L;
            if (memUsageStr.contains("/")) {
                String[] memParts = memUsageStr.split("/");
                memoryUsedBytes = parseBytes(memParts[0]);
                memoryLimitBytes = parseBytes(memParts[1]);
            }

            double memPercent = parsePercentage(parts[4]);
            String netIO = parts.length > 5 ? parts[5].trim() : "0B / 0B";
            String blockIO = parts.length > 6 ? parts[6].trim() : "0B / 0B";

            String status = "HEALTHY";
            if (cpuPercent > 90.0 || memPercent > 90.0) {
                status = "CRITICAL";
            } else if (cpuPercent > 75.0 || memPercent > 75.0) {
                status = "WARNING";
            }

            return ContainerStats.builder()
                    .containerId(id)
                    .name(name.isEmpty() ? containerName : name)
                    .cpuPercent(cpuPercent)
                    .memoryUsedBytes(memoryUsedBytes)
                    .memoryLimitBytes(memoryLimitBytes)
                    .memoryPercent(memPercent)
                    .netIO(netIO)
                    .blockIO(blockIO)
                    .status(status)
                    .build();
        } catch (Exception e) {
            logger.warn("Error parsing container stats for {}: {}", containerName, e.getMessage());
            return ContainerStats.defaultFor(containerName, "UNKNOWN");
        }
    }

    public List<String> fetchContainerLogs(String containerName, int tailLines) {
        if (!dockerEnabled || containerName == null || containerName.isBlank()) {
            return List.of();
        }

        int tail = tailLines > 0 ? tailLines : 50;
        List<String> command = List.of(
                "docker", "logs", "--tail", String.valueOf(tail), "--timestamps", containerName
        );

        CommandResult result = executeCommand(command, 10);
        if (result.exitCode() != 0) {
            logger.debug("Failed fetching logs for container {}: {}", containerName, result.stderr());
            return List.of();
        }

        String output = result.stdout();
        if (output.isBlank() && !result.stderr().isBlank()) {
            output = result.stderr();
        }

        return Arrays.stream(output.split("\\r?\\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    public void streamContainerLogs(String containerName, Consumer<String> lineConsumer, AtomicBoolean runningFlag) {
        if (!dockerEnabled || containerName == null || containerName.isBlank()) {
            return;
        }

        Process process = null;
        try {
            List<String> command = List.of("docker", "logs", "--follow", "--tail", "50", "--timestamps", containerName);
            ProcessBuilder pb = new ProcessBuilder(command);
            try {
                process = pb.start();
            } catch (Exception e) {
                logger.debug("Direct docker start failed, trying sg docker: {}", e.getMessage());
                process = new ProcessBuilder("sg", "docker", "-c", "docker logs --follow --tail 50 --timestamps " + containerName).start();
            }

            Process p = process;
            CompletableFuture<Void> errFuture = CompletableFuture.runAsync(() -> {
                try (BufferedReader errReader = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                    String errLine;
                    while (runningFlag.get() && (errLine = errReader.readLine()) != null) {
                        if (errLine.contains("permission denied")) {
                            logger.warn("Docker permission denied streaming logs for {}", containerName);
                            break;
                        }
                        lineConsumer.accept(errLine);
                    }
                } catch (Exception ignored) {
                }
            });

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while (runningFlag.get() && (line = reader.readLine()) != null) {
                    lineConsumer.accept(line);
                }
            }

            errFuture.cancel(true);
        } catch (Exception e) {
            logger.warn("Exception while streaming logs for {}: {}", containerName, e.getMessage());
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    private double parsePercentage(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0.0;
        }
        try {
            String cleaned = raw.replace("%", "").trim().replace(",", ".");
            return Double.parseDouble(cleaned);
        } catch (Exception e) {
            return 0.0;
        }
    }

    public static long parseBytes(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        String cleaned = raw.trim().replace(",", ".");
        Matcher m = VALUE_UNIT_PATTERN.matcher(cleaned);
        if (!m.find()) {
            return 0L;
        }
        try {
            double value = Double.parseDouble(m.group(1));
            String unit = m.group(2) != null ? m.group(2).trim().toUpperCase() : "B";
            return switch (unit) {
                case "B" -> (long) value;
                case "KB" -> (long) (value * 1000);
                case "KIB" -> (long) (value * 1024);
                case "MB" -> (long) (value * 1000 * 1000);
                case "MIB" -> (long) (value * 1024 * 1024);
                case "GB" -> (long) (value * 1000 * 1000 * 1000);
                case "GIB" -> (long) (value * 1024 * 1024 * 1024);
                case "TB" -> (long) (value * 1000L * 1000 * 1000 * 1000);
                case "TIB" -> (long) (value * 1024L * 1024 * 1024 * 1024);
                default -> (long) value;
            };
        } catch (Exception e) {
            return 0L;
        }
    }

    private CommandResult executeCommand(List<String> command, long timeoutSeconds) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();

            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> readStream(process.getInputStream()));
            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                stdoutFuture.cancel(true);
                stderrFuture.cancel(true);
                return new CommandResult(-1, "", "Timeout");
            }

            int exitCode = process.exitValue();
            String stdout = stdoutFuture.get(2, TimeUnit.SECONDS);
            String stderr = stderrFuture.get(2, TimeUnit.SECONDS);

            if (exitCode != 0 && (stderr.contains("permission denied") || stderr.contains("dial unix /var/run/docker.sock"))) {
                // Fallback to sg docker -c
                List<String> sgCmd = new ArrayList<>();
                sgCmd.add("sg");
                sgCmd.add("docker");
                sgCmd.add("-c");
                sgCmd.add(String.join(" ", command));

                Process sgProcess = new ProcessBuilder(sgCmd).start();
                CompletableFuture<String> sgStdout = CompletableFuture.supplyAsync(() -> readStream(sgProcess.getInputStream()));
                CompletableFuture<String> sgStderr = CompletableFuture.supplyAsync(() -> readStream(sgProcess.getErrorStream()));

                boolean sgFinished = sgProcess.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                if (!sgFinished) {
                    sgProcess.destroyForcibly();
                    sgStdout.cancel(true);
                    sgStderr.cancel(true);
                    return new CommandResult(-1, "", "Timeout via sg");
                }
                return new CommandResult(sgProcess.exitValue(), sgStdout.get(2, TimeUnit.SECONDS), sgStderr.get(2, TimeUnit.SECONDS));
            }

            return new CommandResult(exitCode, stdout, stderr);
        } catch (Exception e) {
            logger.debug("Command failed: {}", e.getMessage());
            return new CommandResult(-1, "", e.getMessage());
        }
    }

    private String readStream(InputStream is) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!sb.isEmpty()) {
                    sb.append("\n");
                }
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public record CommandResult(int exitCode, String stdout, String stderr) {
    }
}

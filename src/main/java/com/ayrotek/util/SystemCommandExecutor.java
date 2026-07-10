package com.ayrotek.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class SystemCommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(SystemCommandExecutor.class);

    /**
     * Executes a command given as a single string (split on spaces).
     * Used by HeartbeatService.
     */
    public CommandResult execute(String command, long timeoutSeconds) {
        log.debug("Executing command: {}", command);
        ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            }

            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("Command timed out after {} seconds: {}", timeoutSeconds, command);
                return new CommandResult(1, "", "Timeout");
            }

            int exitCode = process.exitValue();
            String outputStr = output.toString().trim();
            if (exitCode != 0) {
                log.warn("Command failed with exit code {}: {}. Output: {}", exitCode, command, outputStr);
            }
            return new CommandResult(exitCode, outputStr, null);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Command execution interrupted: {}", command);
            return new CommandResult(1, "", e.getMessage());
        } catch (java.io.IOException e) {
            // Command binary not found or OS-level launch failure — expected for optional tools
            log.debug("Command not available on this system: '{}'. Reason: {}", command, e.getMessage());
            return new CommandResult(1, "", e.getMessage());
        } catch (Exception e) {
            log.warn("Unexpected error executing command '{}': {}", command, e.getMessage());
            return new CommandResult(1, "", e.getMessage());
        }
    }

    /**
     * Executes a command given as a token list with a Duration timeout.
     * Used by inventory providers (NvidiaGpuInventoryProvider, AmdGpuInventoryProvider).
     */
    public CommandResult execute(List<String> command, Duration timeout) {
        log.debug("Executing command: {}", String.join(" ", command));
        ProcessBuilder processBuilder = new ProcessBuilder(command);

        try {
            Process process = processBuilder.start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            try (BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    log.warn("Command timed out after {}ms: {}", timeout.toMillis(), String.join(" ", command));
                    return new CommandResult(1, "", "Timeout");
                }

                String line;
                while ((line = stdoutReader.readLine()) != null) {
                    stdout.append(line).append(System.lineSeparator());
                }
                while ((line = stderrReader.readLine()) != null) {
                    stderr.append(line).append(System.lineSeparator());
                }
            }

            int exitCode = process.exitValue();
            String stdoutStr = stdout.toString().trim();
            String stderrStr = stderr.toString().trim();

            if (exitCode != 0 && !stderrStr.isEmpty()) {
                log.warn("Command execution resulted in an error (exit code {}): {}", exitCode, stderrStr);
            }

            return new CommandResult(exitCode, stdoutStr, stderrStr);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Command execution was interrupted: {}", String.join(" ", command), e);
            return new CommandResult(1, "", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to execute command: {}", String.join(" ", command), e);
            return new CommandResult(1, "", e.getMessage());
        }
    }

    /**
     * CommandResult unifies both execution paths.
     * <ul>
     *   <li>{@code output()} — stdout from the single-string execute overload</li>
     *   <li>{@code stdout()} — alias for output(), used by inventory providers</li>
     *   <li>{@code error()} — stderr/error from the single-string execute overload</li>
     *   <li>{@code stderr()} — alias for error(), used by inventory providers</li>
     * </ul>
     */
    public record CommandResult(int exitCode, String output, String error) {
        public String stdout() { return output; }
        public String stderr() { return error; }
    }
}


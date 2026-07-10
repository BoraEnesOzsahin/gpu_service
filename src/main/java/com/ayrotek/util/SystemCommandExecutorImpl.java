package com.ayrotek.util;

import com.ayrotek.exception.CommandExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class SystemCommandExecutorImpl implements SystemCommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(SystemCommandExecutorImpl.class);

    @Override
    public CommandResult execute(List<String> command, Duration timeout) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Process process = null;
        try {
            log.debug("Executing command: {}", String.join(" ", command));
            process = processBuilder.start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            try (BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    throw new CommandExecutionException("Command timed out: " + String.join(" ", command));
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
            log.debug("Command finished with exit code {}. Command: {}", exitCode, String.join(" ", command));
            
            String stdoutStr = stdout.toString().trim();
            String stderrStr = stderr.toString().trim();

            if (exitCode != 0 && !stderrStr.isEmpty()) {
                log.warn("Command execution resulted in an error (exit code {}): {}", exitCode, stderrStr);
            }

            return new CommandResult(exitCode, stdoutStr, stderrStr);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CommandExecutionException("Command execution was interrupted: " + String.join(" ", command), e);
        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            throw new CommandExecutionException("Failed to execute command: " + String.join(" ", command), e);
        }
    }
}

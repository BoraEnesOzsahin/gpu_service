package com.ayrotek.inventory;

import com.ayrotek.dto.ComputeCapability;
import com.ayrotek.dto.GpuInventory;
import com.ayrotek.util.SystemCommandExecutor;
import com.ayrotek.util.SystemCommandExecutor.CommandResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class NvidiaGpuInventoryProvider implements GpuInventoryProvider {

    private static final Logger log = LoggerFactory.getLogger(NvidiaGpuInventoryProvider.class);
    private static final Duration CMD_TIMEOUT = Duration.ofSeconds(10);
    private static final String NOT_AVAILABLE = "[N/A]";

    private final SystemCommandExecutor executor;
    private Boolean available;

    public NvidiaGpuInventoryProvider(SystemCommandExecutor executor) {
        this.executor = executor;
    }

    @Override
    public boolean isAvailable() {
        if (available == null) {
            try {
                CommandResult result = executor.execute(List.of("nvidia-smi", "-L"), CMD_TIMEOUT);
                available = result.exitCode() == 0 && !result.stdout().isEmpty();
            } catch (Exception e) {
                log.warn("NVIDIA SMI tool not available or failed to execute.", e);
                available = false;
            }
        }
        return available;
    }

    @Override
    public List<GpuInventory> detectGpus() {
        if (!isAvailable()) {
            return Collections.emptyList();
        }
        try {
            List<String> command = List.of(
                "nvidia-smi",
                "--query-gpu=index,pci.bus_id,uuid,name,power.default_limit,power.limit,power.max_limit",
                "--format=csv,noheader,nounits"
            );
            CommandResult result = executor.execute(command, CMD_TIMEOUT);

            if (result.exitCode() != 0 || result.stdout().isEmpty()) {
                log.error("nvidia-smi query failed. Exit code: {}. Stderr: {}", result.exitCode(), result.stderr());
                return Collections.emptyList();
            }

            return Arrays.stream(result.stdout().split(System.lineSeparator()))
                .map(this::parseGpuLine)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to detect NVIDIA GPUs.", e);
            return Collections.emptyList();
        }
    }

    private GpuInventory parseGpuLine(String line) {
        try {
            String[] values = Arrays.stream(line.split(",")).map(String::trim).toArray(String[]::new);
            // index, pci.bus_id, uuid, name, power.default_limit, power.limit, power.max_limit
            if (values.length < 7) {
                log.warn("Skipping malformed nvidia-smi line: {}", line);
                return null;
            }

            String index = values[0];
            String pciBusId = values[1];
            String uuid = values[2];
            String name = values[3];

            String gpuId = getGpuId(pciBusId, uuid, index);
            Integer tdp = getTdpInWatts(values[4], values[5], values[6]);

            GpuInventory inventory = new GpuInventory();
            inventory.setGpuId(gpuId);
            inventory.setName(name);
            inventory.setTdpW(tdp);
            // As per requirements, set compute capability to 0
            inventory.setComputeCapability(new ComputeCapability(0, "MH/s"));

            return inventory;
        } catch (Exception e) {
            log.error("Failed to parse NVIDIA GPU data line: '{}'", line, e);
            return null;
        }
    }

    private String getGpuId(String pciBusId, String uuid, String index) {
        if (isValid(pciBusId)) return pciBusId;
        if (isValid(uuid)) return uuid;
        return index; // Fallback to index
    }

    private Integer getTdpInWatts(String defaultLimit, String currentLimit, String maxLimit) {
        return parsePowerValue(defaultLimit)
            .or(() -> parsePowerValue(currentLimit))
            .or(() -> parsePowerValue(maxLimit))
            .orElse(0); // Fallback to 0 as per requirements
    }

    private Optional<Integer> parsePowerValue(String powerStr) {
        if (!isValid(powerStr)) {
            return Optional.empty();
        }
        try {
            return Optional.of((int) Double.parseDouble(powerStr));
        } catch (NumberFormatException e) {
            log.warn("Could not parse power value: {}", powerStr);
            return Optional.empty();
        }
    }

    private boolean isValid(String value) {
        return value != null && !value.isEmpty() && !value.equals(NOT_AVAILABLE);
    }
}

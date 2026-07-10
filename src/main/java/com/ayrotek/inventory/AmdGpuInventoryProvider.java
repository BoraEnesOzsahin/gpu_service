package com.ayrotek.inventory;

import com.ayrotek.dto.ComputeCapability;
import com.ayrotek.dto.GpuInventory;
import com.ayrotek.util.SystemCommandExecutor;
import com.ayrotek.util.SystemCommandExecutor.CommandResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
public class AmdGpuInventoryProvider implements GpuInventoryProvider {

    private static final Logger log = LoggerFactory.getLogger(AmdGpuInventoryProvider.class);
    private static final Duration CMD_TIMEOUT = Duration.ofSeconds(10);

    private final SystemCommandExecutor executor;
    private final ObjectMapper objectMapper;
    private Boolean available;

    public AmdGpuInventoryProvider(SystemCommandExecutor executor, ObjectMapper objectMapper) {
        this.executor = executor;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isAvailable() {
        if (available == null) {
            try {
                // HiveOS native AMD info command via bash to ensure PATH/alias resolution
                CommandResult result = executor.execute(List.of("bash", "-c", "amd-info"), CMD_TIMEOUT);
                if (result.exitCode() == 0 && !result.stdout().isEmpty()) {
                    available = true;
                    return true;
                }

                // Fallback to rocm-smi / amd-smi
                result = executor.execute(List.of("bash", "-c", "rocm-smi --version"), CMD_TIMEOUT);
                if (result.exitCode() != 0) {
                    result = executor.execute(List.of("bash", "-c", "amd-smi --version"), CMD_TIMEOUT);
                }
                available = result.exitCode() == 0;
            } catch (Exception e) {
                log.warn("AMD/ROCm SMI tool not available or failed to execute.", e);
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
            // 1. Try HiveOS amd-info via bash
            CommandResult result = executor.execute(List.of("bash", "-c", "amd-info"), CMD_TIMEOUT);
            if (result.exitCode() == 0 && !result.stdout().isEmpty()) {
                List<GpuInventory> gpus = parseAmdInfo(result.stdout());
                if (!gpus.isEmpty()) {
                    return gpus;
                }
            }

            // 2. ROCm SMI is the modern tool, prefer it. It has a stable JSON output format.
            result = executor.execute(List.of("bash", "-c", "rocm-smi --show-static-info -a --json"), CMD_TIMEOUT);
            
            // 3. Fallback for older amd-smi versions which might have a different command structure
            if (result.exitCode() != 0) {
                log.warn("`rocm-smi --show-static-info -a --json` failed, trying `amd-smi static --json`...");
                result = executor.execute(List.of("bash", "-c", "amd-smi static --json"), CMD_TIMEOUT);
            }

            if (result.exitCode() != 0 || result.stdout().isEmpty()) {
                log.error("amd-smi/rocm-smi query failed. Exit code: {}. Stderr: {}", result.exitCode(), result.stderr());
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(result.stdout());
            List<GpuInventory> gpus = new ArrayList<>();

            // The JSON structure from rocm-smi is typically a map of "cardX" -> {details}
            root.fields().forEachRemaining(entry -> {
                String cardKey = entry.getKey();
                if (cardKey.startsWith("card")) {
                    JsonNode gpuNode = entry.getValue();
                    parseGpuNode(gpuNode).ifPresent(gpus::add);
                }
            });
            return gpus;

        } catch (Exception e) {
            log.error("Failed to detect AMD GPUs.", e);
            return Collections.emptyList();
        }
    }

    private List<GpuInventory> parseAmdInfo(String output) {
        // Strip ANSI escape codes (colors) which are common in HiveOS outputs
        String cleanOutput = output.replaceAll("\u001B\\[[;\\d]*m", "");
        List<GpuInventory> gpus = new ArrayList<>();
        String[] blocks = cleanOutput.split("=== GPU ");
        for (int i = 1; i < blocks.length; i++) {
            try {
                String block = blocks[i];
                String[] lines = block.split("\n");
                String header = lines[0].replace("===", "").trim();
                String[] headerParts = header.split(",", 2);
                if (headerParts.length < 2) continue;

                String busAndName = headerParts[1].trim();
                int spaceIdx = busAndName.indexOf(" ");
                if (spaceIdx == -1) continue;

                String busId = busAndName.substring(0, spaceIdx).trim();
                String name = busAndName.substring(spaceIdx + 1).trim();

                // Normalize bus ID to match format "0000:23:00.0"
                if (busId.split(":").length == 2) {
                    busId = "0000:" + busId;
                }

                int tdp = 0;
                for (String line : lines) {
                    if (line.contains("Cap: ")) {
                        String capStr = line.substring(line.indexOf("Cap: ") + 5);
                        int endIdx = capStr.indexOf("W");
                        if (endIdx != -1) {
                            capStr = capStr.substring(0, endIdx).trim();
                        }
                        try {
                            tdp = Integer.parseInt(capStr);
                        } catch (NumberFormatException ignored) {}
                    }
                }

                GpuInventory inventory = new GpuInventory();
                inventory.setGpuId(busId);
                inventory.setName(name);
                inventory.setTdpW(tdp);
                inventory.setComputeCapability(new ComputeCapability(0, "MH/s"));
                gpus.add(inventory);
            } catch (Exception e) {
                log.warn("Failed to parse amd-info block", e);
            }
        }
        return gpus;
    }

    private Optional<GpuInventory> parseGpuNode(JsonNode gpuNode) {
        try {
            String pciBus = getText(gpuNode, "PCI Bus");
            String uuid = getText(gpuNode, "GPU UUID");
            String cardModel = getText(gpuNode, "Card model");
            String cardSeries = getText(gpuNode, "Card series");
            
            String name = Optional.ofNullable(cardModel).filter(s -> !s.isBlank())
                .orElseGet(() -> Optional.ofNullable(cardSeries).filter(s -> !s.isBlank()).orElse("Unknown AMD GPU"));

            String gpuId = getGpuId(pciBus, uuid, gpuNode.has("GPU ID") ? gpuNode.get("GPU ID").asText("") : "");

            // Power limit parsing. ROCm SMI provides "Board Power Limit".
            // This is the most reliable value for TDP.
            Integer tdp = parsePowerValue(gpuNode, "Board Power Limit")
                .or(() -> parsePowerValue(gpuNode, "Default Power Limit"))
                .orElse(0);

            GpuInventory inventory = new GpuInventory();
            inventory.setGpuId(gpuId);
            inventory.setName(name);
            inventory.setTdpW(tdp);
            inventory.setComputeCapability(new ComputeCapability(0, "MH/s"));

            return Optional.of(inventory);
        } catch (Exception e) {
            log.error("Failed to parse AMD GPU data node: {}", gpuNode.toPrettyString(), e);
            return Optional.empty();
        }
    }
    
    private String getText(JsonNode node, String fieldName) {
        return Optional.ofNullable(node.get(fieldName)).map(JsonNode::asText).orElse(null);
    }

    private String getGpuId(String pciBus, String uuid, String index) {
        if (pciBus != null && !pciBus.isBlank()) return pciBus;
        if (uuid != null && !uuid.isBlank()) return uuid;
        return index;
    }

    private Optional<Integer> parsePowerValue(JsonNode node, String fieldName) {
        String powerStr = getText(node, fieldName);
        if (powerStr == null || powerStr.isBlank()) {
            return Optional.empty();
        }
        try {
            // Example: "255.0 W"
            String numericPart = powerStr.split(" ")[0];
            return Optional.of((int) Double.parseDouble(numericPart));
        } catch (Exception e) {
            log.warn("Could not parse AMD power value for field '{}': {}", fieldName, powerStr);
            return Optional.empty();
        }
    }
}

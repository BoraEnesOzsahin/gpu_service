package com.ayrotek.service;

import com.ayrotek.config.EmsProperties;
import com.ayrotek.config.HeartbeatProperties;
import com.ayrotek.dto.heartbeat.CurrentPerformance;
import com.ayrotek.dto.heartbeat.GpuTelemetry;
import com.ayrotek.dto.heartbeat.HeartbeatEmsResponse;
import com.ayrotek.dto.heartbeat.HeartbeatRequest;
import com.ayrotek.dto.heartbeat.SystemMetrics;
import com.ayrotek.exception.EmsConnectionException;
import com.ayrotek.exception.EmsTimeoutException;
import com.ayrotek.exception.NodeNotActiveException;
import com.ayrotek.model.NodeStatus;
import com.ayrotek.store.NodeRegistrationStore;
import com.ayrotek.util.SystemCommandExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.time.Instant;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class HeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatService.class);

    private static final String NVIDIA_SMI_COMMAND =
            "nvidia-smi --query-gpu=pci.bus_id,utilization.gpu,temperature.gpu,power.draw --format=csv,noheader,nounits";

    private final NodeRegistrationStore nodeRegistrationStore;
    private final SystemCommandExecutor commandExecutor;
    private final HeartbeatProperties heartbeatProperties;
    private final EmsProperties emsProperties;
    private final RestClient emsRestClient;
    private final ObjectMapper objectMapper;

    public HeartbeatService(
            NodeRegistrationStore nodeRegistrationStore,
            SystemCommandExecutor commandExecutor,
            HeartbeatProperties heartbeatProperties,
            EmsProperties emsProperties,
            RestClient emsRestClient,
            ObjectMapper objectMapper) {
        this.nodeRegistrationStore = nodeRegistrationStore;
        this.commandExecutor = commandExecutor;
        this.heartbeatProperties = heartbeatProperties;
        this.emsProperties = emsProperties;
        this.emsRestClient = emsRestClient;
        this.objectMapper = objectMapper;
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Builds a heartbeat request from real system and GPU telemetry.
     * <p>
     * Node initialization is NOT required. If the node has a stored {@code node_id},
     * it will be included; otherwise {@code node_id} is {@code null}.
     * This method never throws {@link NodeNotActiveException} — it is safe to call
     * for preview purposes regardless of registration status.
     */
    public HeartbeatRequest buildHeartbeatRequest() {
        log.info("Building heartbeat request (preview)...");

        // node_id is optional for preview — use if available, null otherwise
        String nodeId = nodeRegistrationStore.getNodeId().orElse(null);
        if (nodeId != null) {
            log.info("Using stored node ID for heartbeat preview: {}", nodeId);
        } else {
            log.info("Node ID not available (node not yet initialized). Building preview without node_id.");
        }

        long timeoutSeconds = heartbeatProperties.getCommandTimeout().toSeconds();

        List<GpuTelemetry> nvidiaTelemetry = collectNvidiaTelemetry(timeoutSeconds);
        log.info("Collected telemetry for {} NVIDIA GPU(s).", nvidiaTelemetry.size());

        List<GpuTelemetry> amdTelemetry = collectAmdTelemetry(timeoutSeconds);
        log.info("Collected telemetry for {} AMD GPU(s).", amdTelemetry.size());

        List<GpuTelemetry> allGpuTelemetry = mergeAndDeduplicate(nvidiaTelemetry, amdTelemetry);
        log.info("Total GPU telemetry entries: {}", allGpuTelemetry.size());

        // Update performance metrics dynamically from miner
        Map<String, CurrentPerformance> perfMap = fetchMinerPerformance(timeoutSeconds);
        if (!perfMap.isEmpty()) {
            List<GpuTelemetry> updatedTelemetry = new ArrayList<>();
            for (GpuTelemetry gpu : allGpuTelemetry) {
                CurrentPerformance perf = perfMap.getOrDefault(gpu.gpuId(), gpu.currentPerformance());
                updatedTelemetry.add(new GpuTelemetry(gpu.gpuId(), gpu.loadPct(), gpu.tempC(), gpu.powerDrawW(), perf));
            }
            allGpuTelemetry = updatedTelemetry;
        }

        NodeStatus nodeStatus = determineStatus(allGpuTelemetry);
        log.info("Determined node status: {}", nodeStatus.getValue());

        Double systemTemp = readSystemTemperature(timeoutSeconds);

        SystemMetrics metrics = new SystemMetrics(systemTemp, nodeStatus);

        return new HeartbeatRequest(nodeId, Instant.now(), metrics, allGpuTelemetry);
    }

    /**
     * Builds a heartbeat request from real telemetry and POSTs it to EMS using
     * the stored Bearer Token. Returns the EMS response (status + body) unchanged.
     * <p>
     * Requires the node to be in {@code ACTIVE} status with a valid {@code node_id}
     * and {@code api_token}. Throws {@link NodeNotActiveException} otherwise.
     */
    public ResponseEntity<Object> sendHeartbeat() {
        log.info("Starting heartbeat submission to EMS...");

        // --- Guard: node must be ACTIVE with credentials ---
        if (!nodeRegistrationStore.isActive()) {
            throw new NodeNotActiveException(
                    "Heartbeat cannot be sent before the node is active. Current status: "
                            + nodeRegistrationStore.getStatus());
        }

        String nodeId = nodeRegistrationStore.getNodeId()
                .orElseThrow(() -> new NodeNotActiveException(
                        "Heartbeat cannot be sent: node_id is missing."));

        String apiToken = nodeRegistrationStore.getApiToken()
                .orElseThrow(() -> new NodeNotActiveException(
                        "Heartbeat cannot be sent: api_token is missing."));

        log.info("Sending heartbeat for node ID: {}", nodeId);

        // --- Build heartbeat from real telemetry ---
        HeartbeatRequest heartbeatRequest = buildHeartbeatRequest();

        // Ensure the request carries the real node_id (buildHeartbeatRequest returns
        // whatever is stored, but since we confirmed it above, it should be non-null)
        if (heartbeatRequest.nodeId() == null) {
            throw new NodeNotActiveException(
                    "Heartbeat cannot be sent: node_id resolved to null after ACTIVE check.");
        }

        log.info("Heartbeat request built with {} GPU(s). Sending to EMS: {}{}",
                heartbeatRequest.gpuTelemetry().size(),
                emsProperties.getBaseUrl(),
                emsProperties.getHeartbeatPath());

        // --- POST to EMS ---
        return postToEms(heartbeatRequest, apiToken);
    }

    // =========================================================================
    // EMS HTTP call
    // =========================================================================

    private ResponseEntity<Object> postToEms(HeartbeatRequest heartbeatRequest, String apiToken) {
        String heartbeatPath = emsProperties.getHeartbeatPath();
        try {
            ResponseEntity<Object> emsResponse = emsRestClient.post()
                    .uri(heartbeatPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                    .body(heartbeatRequest)
                    .retrieve()
                    .onStatus(status -> true, (req, res) -> {
                        // Suppress default error handling — we want raw status + body
                    })
                    .toEntity(Object.class);

            int statusCode = emsResponse.getStatusCode().value();
            log.info("EMS heartbeat response: HTTP {}", statusCode);

            // Parse and log EMS response (best-effort; does not affect returned response)
            parseAndLogEmsResponse(emsResponse.getBody(), statusCode);

            // Forward EMS status + body unchanged
            return ResponseEntity
                    .status(emsResponse.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(emsResponse.getBody());

        } catch (ResourceAccessException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException) {
                log.error("Timeout while sending heartbeat to EMS at {}{}: {}",
                        emsProperties.getBaseUrl(), heartbeatPath, e.getMessage());
                throw new EmsTimeoutException(
                        "Heartbeat timed out while connecting to EMS.", e);
            }
            log.error("Could not reach EMS at {}{}: {}",
                    emsProperties.getBaseUrl(), heartbeatPath, e.getMessage());
            throw new EmsConnectionException(
                    "Could not connect to EMS for heartbeat submission.", e);
        } catch (RestClientResponseException e) {
            // Should not happen with onStatus override, but handle defensively
            log.warn("EMS returned error status {}: {}", e.getStatusCode(), e.getMessage());
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    private void parseAndLogEmsResponse(Object body, int httpStatus) {
        if (body == null) {
            log.debug("EMS heartbeat response body is empty (HTTP {}).", httpStatus);
            return;
        }
        try {
            HeartbeatEmsResponse emsResponse = objectMapper.convertValue(body, HeartbeatEmsResponse.class);
            log.info("Heartbeat accepted by EMS: command={}, setpointPowerW={}, nextHeartbeat={}",
                    emsResponse.command(),
                    emsResponse.setpointPowerW(),
                    emsResponse.nextHeartbeat());
            if (emsResponse.jobControl() != null) {
                log.debug("EMS job_control: keepCurrentJob={}", emsResponse.jobControl().keepCurrentJob());
            }
        } catch (Exception e) {
            // Non-JSON or unexpected format — not an error for the caller
            log.debug("EMS heartbeat response body is not a parseable HeartbeatEmsResponse (HTTP {}): {}",
                    httpStatus, e.getMessage());
        }
    }

    // =========================================================================
    // NVIDIA telemetry
    // =========================================================================

    private List<GpuTelemetry> collectNvidiaTelemetry(long timeoutSeconds) {
        SystemCommandExecutor.CommandResult result;
        try {
            result = commandExecutor.execute(NVIDIA_SMI_COMMAND, timeoutSeconds);
        } catch (Exception e) {
            log.warn("nvidia-smi command could not be executed: {}", e.getMessage());
            return List.of();
        }

        if (result.exitCode() != 0) {
            log.warn("nvidia-smi exited with code {}. Error: {}", result.exitCode(), result.error());
            return List.of();
        }

        if (result.output() == null || result.output().isBlank()) {
            log.warn("nvidia-smi returned empty output.");
            return List.of();
        }

        List<GpuTelemetry> telemetryList = new ArrayList<>();
        String[] lines = result.output().split(System.lineSeparator());

        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            parseNvidiaLine(line).ifPresent(telemetryList::add);
        }

        return telemetryList;
    }

    private Optional<GpuTelemetry> parseNvidiaLine(String line) {
        try {
            // Expected CSV fields: pci.bus_id, utilization.gpu, temperature.gpu, power.draw
            String[] parts = line.split(",");
            if (parts.length < 4) {
                log.warn("Skipping malformed nvidia-smi output line (expected 4 fields): '{}'", line);
                return Optional.empty();
            }

            String gpuId = normalizePciBusId(parts[0].trim());
            Double loadPct = parseDoubleField(parts[1].trim(), "utilization.gpu", line);
            Double tempC = parseDoubleField(parts[2].trim(), "temperature.gpu", line);
            Double powerDrawW = parseDoubleField(parts[3].trim(), "power.draw", line);

            if (gpuId == null || gpuId.isBlank()) {
                log.warn("Skipping NVIDIA GPU line with empty gpu_id: '{}'", line);
                return Optional.empty();
            }

            log.debug("No miner API available; setting current_performance to 0 MH/s for NVIDIA GPU '{}'.", gpuId);
            return Optional.of(new GpuTelemetry(gpuId, loadPct, tempC, powerDrawW, unavailablePerformance()));

        } catch (Exception e) {
            log.warn("Failed to parse nvidia-smi output line: '{}'. Reason: {}", line, e.getMessage());
            return Optional.empty();
        }
    }

    // =========================================================================
    // AMD telemetry
    // =========================================================================

    private List<GpuTelemetry> collectAmdTelemetry(long timeoutSeconds) {
        // First try HiveOS amd-info via bash to resolve path/aliases
        Optional<String> outputOpt = tryAmdSmiCommand("bash -c \"amd-info\"", timeoutSeconds);
        if (outputOpt.isPresent()) {
            return parseAmdInfoOutput(outputOpt.get());
        }

        // Try rocm-smi first (preferred), then fall back to amd-smi
        outputOpt = tryAmdSmiCommand(
                "rocm-smi --showuse --showtemp --showpower --json",
                timeoutSeconds
        ).or(() -> tryAmdSmiCommand(
                "amd-smi metric --json",
                timeoutSeconds
        ));

        if (outputOpt.isEmpty()) {
            log.warn("No AMD SMI tool or amd-info available. Skipping AMD telemetry.");
            return List.of();
        }

        return parseAmdJson(outputOpt.get());
    }

    private List<GpuTelemetry> parseAmdInfoOutput(String output) {
        // Strip ANSI escape codes (colors)
        String cleanOutput = output.replaceAll("\u001B\\[[;\\d]*m", "");
        List<GpuTelemetry> result = new ArrayList<>();
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
                if (busId.split(":").length == 2) {
                    busId = "0000:" + busId;
                }

                Double loadPct = null;
                Double powerDrawW = null;
                Double tempC = null;

                for (String line : lines) {
                    if (line.contains("Load: ")) {
                        String loadStr = line.substring(line.indexOf("Load: ") + 6);
                        int endIdx = loadStr.indexOf("%");
                        if (endIdx != -1) {
                            loadPct = parseDoubleField(loadStr.substring(0, endIdx).trim(), "Load", line);
                        }
                    }
                    if (line.contains("Power: ")) {
                        String powerStr = line.substring(line.indexOf("Power: ") + 7);
                        int endIdx = powerStr.indexOf("W");
                        if (endIdx != -1) {
                            powerDrawW = parseDoubleField(powerStr.substring(0, endIdx).trim(), "Power", line);
                        }
                    }
                    if (line.contains("Core: ") && line.contains("°C")) {
                        String tempStr = line.substring(line.indexOf("Core: ") + 6);
                        int endIdx = tempStr.indexOf("°C");
                        if (endIdx != -1) {
                            tempC = parseDoubleField(tempStr.substring(0, endIdx).trim(), "Core Temp", line);
                        }
                    }
                }

                result.add(new GpuTelemetry(busId, loadPct, tempC, powerDrawW, unavailablePerformance()));
            } catch (Exception e) {
                log.warn("Failed to parse amd-info block", e);
            }
        }
        return result;
    }

    private Optional<String> tryAmdSmiCommand(String command, long timeoutSeconds) {
        try {
            SystemCommandExecutor.CommandResult result = commandExecutor.execute(command, timeoutSeconds);
            if (result.exitCode() == 0 && result.output() != null && !result.output().isBlank()) {
                return Optional.of(result.output());
            }
            log.warn("AMD SMI command '{}' failed with exit code {}. Error: {}",
                    command, result.exitCode(), result.error());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("AMD SMI command '{}' could not be executed: {}", command, e.getMessage());
            return Optional.empty();
        }
    }

    private List<GpuTelemetry> parseAmdJson(String json) {
        List<GpuTelemetry> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);

            // rocm-smi JSON: { "card0": { ... }, "card1": { ... } }
            // amd-smi metric: similar structure — iterate all fields
            root.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode gpuNode = entry.getValue();
                if (gpuNode != null && gpuNode.isObject()) {
                    parseAmdGpuNode(key, gpuNode).ifPresent(result::add);
                }
            });
        } catch (Exception e) {
            log.warn("Failed to parse AMD SMI JSON output: {}", e.getMessage());
        }
        return result;
    }

    private Optional<GpuTelemetry> parseAmdGpuNode(String cardKey, JsonNode gpuNode) {
        try {
            String gpuId = resolveAmdGpuId(gpuNode, cardKey);
            if (gpuId == null || gpuId.isBlank()) {
                log.warn("Skipping AMD GPU node '{}': could not determine gpu_id.", cardKey);
                return Optional.empty();
            }

            // load_pct: field names vary between rocm-smi versions
            Double loadPct = resolveAmdDouble(gpuNode,
                    "GPU use (%)", "GFX Activity", "GPU activity percent");

            // temp_c: edge temperature is preferred
            Double tempC = resolveAmdDouble(gpuNode,
                    "Temperature (Sensor edge) (C)",
                    "Temperature (Sensor junction) (C)",
                    "Temperature (Sensor memory) (C)",
                    "temperature_edge");

            // power_draw_w: socket or board power
            Double powerDrawW = resolveAmdDouble(gpuNode,
                    "Average Graphics Package Power (W)",
                    "Current Socket Graphics Package Power (W)",
                    "Average Package Power (W)",
                    "power_avg");

            log.debug("No miner API available; setting current_performance to 0 MH/s for AMD GPU '{}'.", gpuId);
            return Optional.of(new GpuTelemetry(gpuId, loadPct, tempC, powerDrawW, unavailablePerformance()));

        } catch (Exception e) {
            log.warn("Failed to parse AMD GPU node '{}': {}", cardKey, e.getMessage());
            return Optional.empty();
        }
    }

    private String resolveAmdGpuId(JsonNode gpuNode, String cardKey) {
        for (String field : new String[]{"PCI Bus", "pci_bus", "GPU Bus", "bus_id"}) {
            JsonNode node = gpuNode.get(field);
            if (node != null && !node.isNull() && !node.asText("").isBlank()) {
                return normalizePciBusId(node.asText());
            }
        }
        for (String field : new String[]{"GPU ID", "gpu_id", "GUID"}) {
            JsonNode node = gpuNode.get(field);
            if (node != null && !node.isNull() && !node.asText("").isBlank()) {
                return node.asText().trim();
            }
        }
        // Last resort: use card key (e.g. "card0")
        return cardKey;
    }

    private Double resolveAmdDouble(JsonNode gpuNode, String... fieldNames) {
        for (String field : fieldNames) {
            JsonNode node = gpuNode.get(field);
            if (node == null || node.isNull()) continue;
            Double parsed = parseDoubleField(node.asText("").trim(), field, null);
            if (parsed != null) return parsed;
        }
        return null;
    }

    // =========================================================================
    // System temperature (optional)
    // =========================================================================

    private Double readSystemTemperature(long timeoutSeconds) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("windows")) {
            return readSystemTemperatureWindows(timeoutSeconds);
        }
        if (os.contains("linux")) {
            return readSystemTemperatureLinux(timeoutSeconds);
        }
        log.debug("System temperature reading not supported on OS: {}", os);
        return null;
    }

    /**
     * Reads system temperature on Linux.
     * First tries /sys/class/thermal (no external tool, available on all modern kernels),
     * then falls back to 'sensors -j' (lm-sensors package).
     */
    private Double readSystemTemperatureLinux(long timeoutSeconds) {
        // User requested specific bash pipeline for sensors
        List<String> cmd = List.of("bash", "-c", "sensors | grep -E '(Tctl|junction|mem):' | grep -oE ':\\s+\\+[0-9]+\\.[0-9]' | grep -oE '[0-9]+\\.[0-9]' | sort -rn | head -n 1");
        
        try {
            SystemCommandExecutor.CommandResult result = commandExecutor.execute(cmd, Duration.ofSeconds(timeoutSeconds));
            if (result.exitCode() == 0 && result.output() != null && !result.output().isBlank()) {
                Double temp = Double.parseDouble(result.output().trim());
                if (temp > 0.0 && temp < 150.0) {
                    log.debug("System temperature read from 'sensors' command pipeline: {}°C", temp);
                    return temp;
                }
            }
        } catch (Exception e) {
            log.debug("User's sensors command pipeline failed: {}", e.getMessage());
        }

        log.debug("Could not read system temperature on Linux.");
        return null;
    }

    /**
     * Reads system temperature on Windows via PowerShell WMI.
     * MSAcpi_ThermalZoneTemperature.CurrentTemperature is in tenths of Kelvin.
     * Formula: (value / 10.0) - 273.15 = Celsius
     */
    private Double readSystemTemperatureWindows(long timeoutSeconds) {
        List<String> cmd = List.of(
                "powershell", "-NoProfile", "-NonInteractive", "-Command",
                "(Get-WmiObject -Namespace root/wmi -Class MSAcpi_ThermalZoneTemperature " +
                        "| Select-Object -First 1).CurrentTemperature"
        );
        try {
            SystemCommandExecutor.CommandResult result =
                    commandExecutor.execute(cmd, Duration.ofSeconds(timeoutSeconds));
            if (result.exitCode() == 0 && result.output() != null && !result.output().isBlank()) {
                String raw = result.output().trim();
                double tenthsOfKelvin = Double.parseDouble(raw);
                double celsius = tenthsOfKelvin / 10.0 - 273.15;
                if (celsius > 0.0 && celsius < 150.0) {
                    log.debug("System temperature read via WMI: {}°C", celsius);
                    return celsius;
                }
                log.debug("WMI returned implausible temperature value: {} tenths-K ({} °C)", tenthsOfKelvin, celsius);
            }
        } catch (Exception e) {
            log.debug("WMI temperature query failed: {}", e.getMessage());
        }
        log.debug("Could not read system temperature on Windows.");
        return null;
    }



    // =========================================================================
    // Status determination
    // =========================================================================

    private NodeStatus determineStatus(List<GpuTelemetry> telemetry) {
        if (telemetry.isEmpty()) {
            return NodeStatus.ERROR;
        }
        double threshold = heartbeatProperties.getWorkingLoadThresholdPct();
        boolean anyWorking = telemetry.stream()
                .anyMatch(gpu -> gpu.loadPct() != null && gpu.loadPct() >= threshold);
        return anyWorking ? NodeStatus.WORKING : NodeStatus.IDLE;
    }

    // =========================================================================
    // Miner Performance (HiveOS)
    // =========================================================================

    private Map<String, CurrentPerformance> fetchMinerPerformance(long timeoutSeconds) {
        Map<String, CurrentPerformance> perfMap = new HashMap<>();

        // Read the last 200 lines of any active miner's log file directly.
        // This avoids the issue of interactive tailing missing the 30-second periodic stats table.
        SystemCommandExecutor.CommandResult result;
        try {
            result = commandExecutor.execute(List.of("bash", "-c", "tail -n 200 /var/log/miner/*/*.log 2>/dev/null"), Duration.ofSeconds(3));
        } catch (Exception e) {
            log.debug("Failed to execute miner command: {}", e.getMessage());
            return perfMap;
        }

        String output = result.output();
        if (output == null || output.isBlank()) {
            return perfMap;
        }

        // Pass 1: Map GPU Index -> PCI Bus ID
        Map<String, String> indexToBusId = new HashMap<>();
        
        // Strategy A: Try to find mapping in the miner output (TeamRedMiner format)
        String[] lines = output.replaceAll("\u001B\\[[;\\d]*m", "").split("\n");
        for (String line : lines) {
            if (line.matches(".*\\]\\s+\\d+\\s+[0-9a-fA-F]{2}:[0-9a-fA-F]{2}\\.[0-9].*")) {
                String[] parts = line.substring(line.indexOf("]") + 1).trim().split("\\s+");
                if (parts.length >= 2) {
                    String index = parts[0];
                    String busId = parts[1];
                    if (busId.split(":").length == 2) {
                        busId = "0000:" + busId;
                    }
                    indexToBusId.put(index, busId);
                }
            }
        }

        // Strategy B: If miner log didn't contain the mapping table, fetch it from amd-info
        if (indexToBusId.isEmpty()) {
            try {
                SystemCommandExecutor.CommandResult amdInfoResult = commandExecutor.execute(List.of("bash", "-c", "amd-info"), Duration.ofSeconds(5));
                if (amdInfoResult.exitCode() == 0 && amdInfoResult.output() != null) {
                    String[] blocks = amdInfoResult.output().replaceAll("\u001B\\[[;\\d]*m", "").split("=== GPU ");
                    for (int i = 1; i < blocks.length; i++) {
                        String block = blocks[i];
                        String header = block.split("\n")[0].replace("===", "").trim();
                        String[] headerParts = header.split(",", 2);
                        if (headerParts.length >= 2) {
                            String idx = headerParts[0].trim();
                            String busAndName = headerParts[1].trim();
                            int spaceIdx = busAndName.indexOf(" ");
                            if (spaceIdx != -1) {
                                String busId = busAndName.substring(0, spaceIdx).trim();
                                if (busId.split(":").length == 2) busId = "0000:" + busId;
                                indexToBusId.put(idx, busId);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to fallback map indices via amd-info: {}", e.getMessage());
            }
        }

        // Pass 2: Extract performance
        // GPU  0 [44C, fan 60%]      ethash: 33.07Mh/s
        Pattern p = Pattern.compile("([0-9.]+)\\s*([kMGT]?H/s)", Pattern.CASE_INSENSITIVE);
        for (String line : lines) {
            try {
                if (line.contains("GPU ") && line.toLowerCase().contains("h/s")) {
                    String afterGpu = line.substring(line.indexOf("GPU ") + 4).trim();
                    String index = afterGpu.split("\\s+")[0].replaceAll("[^0-9]", "");

                    String busId = indexToBusId.get(index);
                    if (busId == null) continue;

                    Matcher m = p.matcher(line);
                    if (m.find()) {
                        double value = Double.parseDouble(m.group(1));
                        String unit = m.group(2);
                        // Normalize capitalization (e.g. Mh/s -> MH/s)
                        if (unit.equalsIgnoreCase("mh/s")) unit = "MH/s";
                        if (unit.equalsIgnoreCase("h/s")) unit = "H/s";

                        perfMap.put(busId, new CurrentPerformance(value, unit));
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to parse miner line: {}", line);
            }
        }

        return perfMap;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private List<GpuTelemetry> mergeAndDeduplicate(List<GpuTelemetry> nvidia, List<GpuTelemetry> amd) {
        Map<String, GpuTelemetry> byId = new LinkedHashMap<>();

        for (GpuTelemetry gpu : nvidia) {
            if (byId.containsKey(gpu.gpuId())) {
                log.warn("Duplicate gpu_id detected in NVIDIA telemetry: '{}'. Keeping first entry.", gpu.gpuId());
            } else {
                byId.put(gpu.gpuId(), gpu);
            }
        }
        for (GpuTelemetry gpu : amd) {
            if (byId.containsKey(gpu.gpuId())) {
                log.warn("Duplicate gpu_id '{}' found in AMD telemetry — already present from NVIDIA. Keeping NVIDIA entry.", gpu.gpuId());
            } else {
                byId.put(gpu.gpuId(), gpu);
            }
        }

        return new ArrayList<>(byId.values());
    }

    /**
     * Normalizes a PCI bus ID to the same format used during GPU inventory
     * (initialize request), ensuring gpu_id consistency between heartbeat
     * and gpu_inventory entries.
     *
     * <p>nvidia-smi returns IDs like "00000000:01:00.0". We strip the leading
     * 8-digit domain segment when it is all zeros and replace it with "0000",
     * producing the canonical form "0000:01:00.0".</p>
     */
    private String normalizePciBusId(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        String trimmed = raw.trim();
        // "00000000:01:00.0" → "0000:01:00.0"
        String[] parts = trimmed.split(":");
        if (parts.length == 3 && parts[0].matches("0{8}")) {
            return "0000:" + parts[1] + ":" + parts[2];
        }
        return trimmed;
    }

    private Double parseDoubleField(String value, String fieldName, String sourceLine) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()
                || trimmed.equalsIgnoreCase("N/A")
                || trimmed.equalsIgnoreCase("[N/A]")) {
            return null;
        }
        // Strip trailing unit if present (e.g. "72 C", "210.50 W")
        String numeric = trimmed.split("\\s+")[0];
        try {
            return Double.parseDouble(numeric);
        } catch (NumberFormatException e) {
            if (sourceLine != null) {
                log.warn("Could not parse field '{}' with value '{}' in line: '{}'", fieldName, value, sourceLine);
            } else {
                log.warn("Could not parse field '{}' with value '{}'", fieldName, value);
            }
            return null;
        }
    }

    private CurrentPerformance unavailablePerformance() {
        return new CurrentPerformance(0.0, "MH/s");
    }
}

package com.ayrotek.service;

import com.ayrotek.dto.GpuInventory;
import com.ayrotek.inventory.GpuInventoryProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class GpuInventoryService {

    private static final Logger log = LoggerFactory.getLogger(GpuInventoryService.class);
    private final List<GpuInventoryProvider> providers;

    public GpuInventoryService(List<GpuInventoryProvider> providers) {
        this.providers = providers;
        log.info("Initialized with {} GPU inventory providers.", providers.size());
    }

    public List<GpuInventory> detectAllGpus() {
        List<GpuInventory> allGpus = providers.stream()
            .filter(provider -> {
                boolean available = provider.isAvailable();
                if (!available) {
                    log.info("{} is not available on this system.", provider.getClass().getSimpleName());
                }
                return available;
            })
            .flatMap(provider -> {
                try {
                    log.info("Detecting GPUs using {}.", provider.getClass().getSimpleName());
                    return provider.detectGpus().stream();
                } catch (Exception e) {
                    log.error("Error detecting GPUs with provider: {}", provider.getClass().getSimpleName(), e);
                    return null; // Stream.flatMap handles null streams
                }
            })
            .collect(Collectors.toList());

        if (allGpus.isEmpty()) {
            log.warn("No GPUs were detected on the system.");
        } else {
            log.info("Detected a total of {} GPUs.", allGpus.size());
        }
        
        // Check for duplicate gpu_id
        long distinctIds = allGpus.stream().map(GpuInventory::getGpuId).distinct().count();
        if (distinctIds < allGpus.size()) {
            log.error("Duplicate gpu_id detected in the final inventory list. This may indicate a problem.");
            // Depending on requirements, you might want to throw an exception or de-duplicate
        }

        return allGpus;
    }
}

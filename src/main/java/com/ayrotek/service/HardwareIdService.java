package com.ayrotek.service;

import com.ayrotek.exception.MacAddressNotFoundException;
import org.springframework.stereotype.Service;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

@Service
public class HardwareIdService {

    public String getHardwareId() {
        try {
            List<NetworkInterface> physicalInterfaces = new ArrayList<>();
            List<NetworkInterface> ethernetInterfaces = new ArrayList<>();

            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(interfaces)) {
                if (isPhysicalInterface(ni)) {
                    if (isEthernet(ni)) {
                        ethernetInterfaces.add(ni);
                    } else {
                        physicalInterfaces.add(ni);
                    }
                }
            }

            // Prefer Ethernet over other physical interfaces (like Wi-Fi)
            Optional<NetworkInterface> chosenInterface = ethernetInterfaces.stream().findFirst()
                    .or(() -> physicalInterfaces.stream().findFirst());

            return chosenInterface
                    .map(this::formatMacAddress)
                    .orElseThrow(() -> new MacAddressNotFoundException("No suitable physical network interface found."));

        } catch (SocketException e) {
            throw new MacAddressNotFoundException("Cannot access network interfaces: " + e.getMessage());
        }
    }

    private boolean isPhysicalInterface(NetworkInterface ni) throws SocketException {
        return ni != null &&
               !ni.isLoopback() &&
               !ni.isVirtual() &&
               ni.isUp() &&
               ni.getHardwareAddress() != null;
    }

    private boolean isEthernet(NetworkInterface ni) {
        String displayName = ni.getDisplayName().toLowerCase();
        return displayName.contains("eth") || displayName.contains("ethernet");
    }

    private String formatMacAddress(NetworkInterface ni) {
        try {
            byte[] mac = ni.getHardwareAddress();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mac.length; i++) {
                sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? ":" : ""));
            }
            return sb.toString();
        } catch (SocketException e) {
            // This should not happen as we've already checked getHardwareAddress()
            throw new MacAddressNotFoundException("Could not retrieve MAC address from interface: " + ni.getDisplayName());
        }
    }
}

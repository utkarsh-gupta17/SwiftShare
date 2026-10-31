package p2p.utils;

import java.security.SecureRandom;

// Helper utilities for generating network ports and random pin codes
public class UploadUtils {

    // Dynamic port range according to IANA guidelines
    private static final int PORT_MIN = 49152;
    private static final int PORT_MAX = 65535;

    private static final SecureRandom RANDOM = new SecureRandom();

    // Pick a random available port in the dynamic range
    public static int generateCode() {
        int range = (PORT_MAX - PORT_MIN) + 1;
        return PORT_MIN + RANDOM.nextInt(range);
    }

    // Generate a 6-digit pin for receiver authentication
    public static String generateSecurityPin() {
        int pin = 100000 + RANDOM.nextInt(900000);
        return String.valueOf(pin);
    }
}

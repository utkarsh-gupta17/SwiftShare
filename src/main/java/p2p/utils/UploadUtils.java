package main.java.p2p.utils;

import java.util.Random;

public class UploadUtils {

    public static int generateCode() {

        // these are the range of availble ports to be used for file sharing
        int DYNAMIC_STARTING_PORT = 49152;
        int DYNAMIC_ENDING_PORT = 85535;

        Random random = new Random();
        // simply return a random port in this range each time
        return random.nextInt((DYNAMIC_ENDING_PORT-DYNAMIC_STARTING_PORT) + DYNAMIC_STARTING_PORT);
    }
}

package com.example.myapplication;

import java.util.HashMap;

/**
 * This class includes the standard GATT attributes of the Multimeter profile.
 */
public class BabyGattAtrributes {
    private static HashMap<String, String> attributes = new HashMap();
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    public static String BABY_SERVICE = "0000fff0-0000-1000-8000-00805f9b34fb";
    public static String BABY_MEASUREMENT = "0000fff4-0000-1000-8000-00805f9b34fb";
    public static String BABY_MODE = "0000fff1-0000-1000-8000-00805f9b34fb";

}
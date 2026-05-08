package com.example.led;

public final class LedUtils {
    public native int device_open();
    public native int LedShowString(String value);
    public native int LED_Enable();
    public native int LED_OFF();
    public native int LED_Colon_Display();
    public native int LED_Pwr_Display();
    public native int LED_Lan_Display();
    public native int LED_Lan_Off();
    public native int LED_Wifi_Fine_Display();
    public native int LED_Wifi_Low_Display();
    public native int LED_Wifi_Off();
}

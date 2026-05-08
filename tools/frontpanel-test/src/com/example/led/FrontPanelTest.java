package com.example.led;

public final class FrontPanelTest {
    private FrontPanelTest() {
    }

    public static void main(String[] args) {
        String lib = args.length > 0 ? args[0] : "/data/local/tmp/libled.so";
        String value = args.length > 1 ? args[1] : "1234";
        System.load(lib);

        LedUtils led = new LedUtils();
        System.out.println("device_open=" + led.device_open());
        System.out.println("LED_Enable=" + led.LED_Enable());
        System.out.println("LED_Pwr_Display=" + led.LED_Pwr_Display());
        System.out.println("LED_Colon_Display=" + led.LED_Colon_Display());
        System.out.println("LED_Lan_Off=" + led.LED_Lan_Off());
        System.out.println("LED_Wifi_Off=" + led.LED_Wifi_Off());
        System.out.println("LedShowString(" + value + ")=" + led.LedShowString(value));
    }
}

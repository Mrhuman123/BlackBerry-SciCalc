package net.mbeffects.calc;

import net.rim.device.api.ui.UiApplication;
import net.rim.device.api.ui.component.Dialog;

/**
 * CalcApp.java
 * Entry point for the MBEffects Scientific Calculator.
 * Initialises the UI application and pushes the main screen.
 *
 * Target: BlackBerry OS 6.0+ (Torch / Bold / Curve)
 * Author: mbeffects
 */
public class CalcApp extends UiApplication {

    public static void main(String[] args) {
        CalcApp app = new CalcApp();
        app.enterEventDispatcher();
    }

    public CalcApp() {
        try {
            CalcMainScreen screen = new CalcMainScreen();
            pushScreen(screen);
        } catch (Throwable t) {
            Dialog.alert("CRASH: " + t.getClass().getName() + "\n" + t.getMessage());
        }
    }
}

package com.genus.usb_comm.bridge;

import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.util.Log;

import com.genus.usb_comm.ble.BLECommunication;
import com.genus.usb_comm.usb.UsbComm;

import java.nio.charset.StandardCharsets;

public class UsbBleBridge {
    private static final String TAG = "UsbBleBridge";

    private final Context context;
    private final UsbComm usbComm;
    private final BLECommunication bleComm;
    private BluetoothGattCharacteristic bleTxChar;

    private boolean running = false;
    private Thread usbReadThread;

    public interface LogCallback {
        void onLog(String msg);
    }

    private LogCallback logCallback;

    public UsbBleBridge(Context ctx, UsbComm usbComm, BLECommunication bleComm) {
        this.context = ctx;
        this.usbComm = usbComm;
        this.bleComm = bleComm;
    }

    public void setLogCallback(LogCallback cb) {
        this.logCallback = cb;
    }

    /**
     * Call once after BLE services are discovered
     */
    public void setBleTxCharacteristic(BluetoothGattCharacteristic characteristic) {
        this.bleTxChar = characteristic;
    }

    /**
     * Start relaying USB → BLE
     */
    public void startBridge() {
        if (usbComm == null || bleComm == null) {
            log("Bridge cannot start: USB or BLE missing");
            return;
        }
        running = true;
        usbReadThread = new Thread(() -> {
            byte[] buffer = new byte[256];
            while (running) {
                int read = usbComm.read(buffer);
                if (read > 0) {
                    byte[] data = new byte[read];
                    System.arraycopy(buffer, 0, data, 0, read);
                    String hex = bytesToHex(data);
                    String ascii = new String(data, StandardCharsets.US_ASCII);

                    log("USB → " + ascii + " [0x" + hex + "]");

                    if (bleTxChar != null) {
//                        bleComm.DlmsWrite(bleTxChar, data);
                        log("Sent to BLE (" + data.length + " bytes)");
                    } else {
                        log("BLE TX characteristic not ready, dropped packet");
                    }
                }

                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        usbReadThread.start();
    }

    public void stopBridge() {
        running = false;
        if (usbReadThread != null) usbReadThread.interrupt();
    }

    private void log(String msg) {
        Log.d(TAG, msg);
        if (logCallback != null) logCallback.onLog(msg);
    }

    private final char[] hexArray = "0123456789ABCDEF".toCharArray();

    private String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; ++j) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}

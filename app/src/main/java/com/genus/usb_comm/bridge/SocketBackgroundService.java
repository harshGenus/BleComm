package com.genus.usb_comm.bridge;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.genus.usb_comm.ble.BLECommunication;
import com.genus.usb_comm.ble.BleComm;
import com.genus.usb_comm.usb.UsbComm;
// OR if you’re using FTDI driver instead:
// import com.genus.usb_comm.usb.UsbSerialCommunication;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SocketBackgroundService extends Service {
    private static final String TAG = "SocketBridgeService";

    private ExecutorService executor;
    private UsbComm usbComm;
    private BLECommunication bleCommunication;
    private Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
        executor = Executors.newFixedThreadPool(2);
        Log.i(TAG, "Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Bridge starting...");

        usbComm = new UsbComm(this);
        bleCommunication = new BLECommunication(this,null);

        if (!usbComm.open()) {
            Log.e(TAG, "USB open failed");
            stopSelf();
            return START_NOT_STICKY;
        }

//        if (!bleCommunication.connect()) {
//            Log.e(TAG, "BLE connect failed");
//            stopSelf();
//            return START_NOT_STICKY;
//        }

        // 🔄 USB → BLE
        executor.execute(() -> {
            byte[] buffer = new byte[256];
            while (!Thread.currentThread().isInterrupted()) {
                int len = usbComm.read(buffer);
                if (len > 0) {
                    byte[] data = new byte[len];
                    System.arraycopy(buffer, 0, data, 0, len);

                     bleCommunication.DlmsWrite(bleCommunication.getWriteCharacteristic(),data);
                    Log.d(TAG, "USB → BLE (): " + bytesToHex(data));
                }
            }
        });

        // 🔄 BLE → USB (callback style)
//        bleCommunication.setReadCallback(data -> {
//            if (data != null && data.length > 0) {
//                usbComm.write(data);
//                Log.d(TAG, "BLE → USB: " + bytesToHex(data));
//            }
//        });

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdownNow();
        if (usbComm != null) usbComm.close();
        if (bleCommunication != null) bleCommunication.disconnect();
        Log.i(TAG, "Bridge stopped");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; ++j) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}

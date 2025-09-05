package com.genus.usb_comm.usb;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;
import com.genus.usb_comm.MainActivity;
import com.genus.usb_comm.SavedPreference;

public class UsbSerialCommunication {

    private static final String TAG = "UsbSerialComm";

    private D2xxManager d2xxManager;
    private FT_Device ftDevice;

    private Context context;

    private boolean isConnected = false;
    private volatile boolean readThreadRunning = false;
    private Thread readThread;

    private final Object bufferLock = new Object();
    private byte[] readBuffer;
    public interface UsbReadCallback {
        void onDataReceived(byte[] data);
    }


    private UsbReadCallback callback;

    public void setUsbReadCallback(UsbReadCallback cb) {
        this.callback = cb;
    }
    // FTDI default VID & PID
    private static final int VID = 0x0403;  // 1027
    private static final int PID = 0x6001;  // 24577 (FT232R, etc.)

    public UsbSerialCommunication(Context ctx) {
        this.context = ctx;
        try {
            d2xxManager = D2xxManager.getInstance(ctx);
        } catch (D2xxManager.D2xxException e) {
            Log.e(TAG, "Unable to get D2xxManager instance", e);
        }
    }
    private void onUsbConnected() {
        Intent intent = new Intent(MainActivity.ConnectionActions.ACTION_USB_CONNECTED);
        context.sendBroadcast(intent);
    }

    private void onUsbDisconnected() {
        Intent intent = new Intent(MainActivity.ConnectionActions.ACTION_USB_DISCONNECTED);
        context.sendBroadcast(intent);
    }

    /** Connect to FTDI device at 9600 8N1 */
    public boolean connect() {


        if (d2xxManager == null) {
            Log.e(TAG, "D2xxManager not initialized");
            return false;
        }

        int devCount = d2xxManager.createDeviceInfoList(context);
        D2xxManager.FtDeviceInfoListNode[] deviceList = new D2xxManager.FtDeviceInfoListNode[devCount];
        d2xxManager.getDeviceInfoList(devCount, deviceList);
        for (int i = 0; i < devCount; i++) {
            Log.i(TAG, "FTDI Device[" + i + "]: " +
                    "Serial=" + deviceList[i].serialNumber +
                    ", Description=" + deviceList[i].description +
                    ", Type=" + deviceList[i].type +
                    ", ID=" + deviceList[i].id +
                    ", LocId=" + deviceList[i].location);
        }
        if (devCount <= 0) {
            Log.e(TAG, "No FTDI device found");
            return false;
        }

        ftDevice = d2xxManager.openByIndex(context, 0);
        if (ftDevice == null || !ftDevice.isOpen()) {
            Log.e(TAG, "Failed to open FTDI device");
            return false;
        }

        // Reset into UART mode
        ftDevice.setBitMode((byte) 0, D2xxManager.FT_BITMODE_RESET);

        ftDevice.setBaudRate(SavedPreference.getBaudRate());
        Log.e("saved baud rate",SavedPreference.getBaudRate()+"");

        int baudRate = SavedPreference.getBaudRate();
        if(baudRate<=0){
            baudRate = 9600;
            SavedPreference.setBaudRate(baudRate);
        }
        ftDevice.setBaudRate(baudRate);
        Log.e("baud rate",baudRate+"");

        ftDevice.setDataCharacteristics(
                D2xxManager.FT_DATA_BITS_8,
                D2xxManager.FT_STOP_BITS_1,
                D2xxManager.FT_PARITY_NONE
        );
        ftDevice.setFlowControl(D2xxManager.FT_FLOW_NONE, (byte) 0x0B, (byte) 0x0D);

        isConnected = true;
        startReadThread();
        onUsbConnected();
        Log.i(TAG, "Connected to FTDI device @" + SavedPreference.getBaudRate() + " 8N1");
        return true;
    }

    public void disconnect() {
        isConnected = false;
        stopReadThread();
        if (ftDevice != null && ftDevice.isOpen()) {
            ftDevice.close();
        }
        onUsbDisconnected();
    }

    /** Write raw bytes */
    public boolean write(byte[] data) {
        if (ftDevice != null && ftDevice.isOpen()) {
            int written = ftDevice.write(data, data.length);
            return written == data.length;
        }
        return false;
    }


    public byte[] getBuffer() {
        synchronized (bufferLock) {
            return readBuffer;
        }
    }

    private void startReadThread() {
        readThreadRunning = true;
        Log.e("readThread", "Started");
        readThread = new Thread(() -> {
            while (readThreadRunning && ftDevice != null && ftDevice.isOpen()) {
                try {
                    int bytesAvailable = ftDevice.getQueueStatus();
                    if (bytesAvailable > 0) {
                        byte[] buffer = new byte[bytesAvailable];
                        int read = ftDevice.read(buffer, bytesAvailable);
                        if (read > 0) {
                            synchronized (bufferLock) {
                                readBuffer = buffer;
                            }
                            String hex  = bytesToHex(buffer).trim();
                            Log.d(TAG, "Read " + read + " bytes: " + hex);
                            if (callback != null) {
                                callback.onDataReceived(buffer);
                            }

                        }
                    }
                    Thread.sleep(100); // small delay
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        readThread.start();
    }

    private void stopReadThread() {
        readThreadRunning = false;
        if (readThread != null) {
            try {
                readThread.join();
            } catch (InterruptedException ignored) {}
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}

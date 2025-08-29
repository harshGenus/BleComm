package com.genus.usb_comm.usb;

import android.content.Context;
import android.util.Log;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;

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
        void onDataReceived(String data);
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

    /** Connect to FTDI device at 9600 8N1 */
    public boolean connect() {
        if (d2xxManager == null) {
            Log.e(TAG, "D2xxManager not initialized");
            return false;
        }

        int devCount = d2xxManager.createDeviceInfoList(context);
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

        // Configure port: 9600 baud, 8 data bits, 1 stop bit, no parity
        ftDevice.setBaudRate(9600);
        ftDevice.setDataCharacteristics(
                D2xxManager.FT_DATA_BITS_8,
                D2xxManager.FT_STOP_BITS_1,
                D2xxManager.FT_PARITY_NONE
        );
        ftDevice.setFlowControl(D2xxManager.FT_FLOW_NONE, (byte) 0x0B, (byte) 0x0D);

        isConnected = true;
        startReadThread();

        Log.i(TAG, "Connected to FTDI device @9600 8N1");
        return true;
    }

    /** Disconnect and stop threads */
    public void disconnect() {
        isConnected = false;
        stopReadThread();

        if (ftDevice != null && ftDevice.isOpen()) {
            ftDevice.close();
            Log.i(TAG, "FTDI device disconnected");
        }
    }

    /** Write raw bytes */
    public boolean write(byte[] data) {
        if (ftDevice != null && ftDevice.isOpen()) {
            int written = ftDevice.write(data, data.length);
            return written == data.length;
        }
        return false;
    }

    /** Write string (ASCII) */
    public boolean write(String msg) {
        return write(msg.getBytes());
    }

    /** Get last buffer read */
    public byte[] getBuffer() {
        synchronized (bufferLock) {
            return readBuffer;
        }
    }


    private void startReadThread() {
        readThreadRunning = true;
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
                            String ascii = hexToAscii(hex);
                            Log.d(TAG, "Read " + read + " bytes: " + hex);
                            Log.e("read Hex ",ascii);
                            if (callback != null) {
                                callback.onDataReceived(ascii);
                            }
                        }
                    }
                    Thread.sleep(1000); // small delay
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        readThread.start();
    }

    public static String hexToAscii(String hexStr) {
        StringBuilder output = new StringBuilder();
        hexStr = hexStr.replaceAll(" ","");
        for (int i = 0; i < hexStr.length(); i += 2) {
            String str = hexStr.substring(i, i + 2);
            output.append((char) Integer.parseInt(str, 16));
        }

        return output.toString();
    }
    private void stopReadThread() {
        readThreadRunning = false;
        if (readThread != null) {
            try {
                readThread.join();
            } catch (InterruptedException ignored) {}
        }
    }

    /** Helper: convert bytes to HEX */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}

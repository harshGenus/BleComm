package com.genus.usb_comm.usb;

import android.content.Context;
import android.util.Log;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;

import java.util.Objects;

abstract class UsbSerialPort {
    private static D2xxManager ftD2xx = null;
    private FT_Device ftDev = null;
    private final Context context;

    private int baudRate;
    private boolean readThreadRunning = false;
    private ReadThread readThread;
    private int dataCount = 0;
    private final byte[] readBuffer = new byte[4096];

     UsbSerialPort(Context context) {
        this.context = context;
    }

    protected boolean connect(String comPort, int baudRate) {
        try {
            ftD2xx = D2xxManager.getInstance(context);
        } catch (D2xxManager.D2xxException e) {
            e.printStackTrace();
            return false;
        }

        this.baudRate = baudRate;

        int devCount = ftD2xx.createDeviceInfoList(context);
        if (devCount <= 0) {
            Log.e("UsbSerialPort", "No FTDI devices found");
            return false;
        }

        ftDev = ftD2xx.openByIndex(context, 0);
        if (ftDev == null || !ftDev.isOpen()) {
            Log.e("UsbSerialPort", "Failed to open device");
            return false;
        }

        ftDev.setBitMode((byte) 0, (byte) 0);
        ftDev.setBaudRate(this.baudRate);
        ftDev.setDataCharacteristics((byte) 8, (byte) 0, (byte) 0);

        readThreadRunning = true;
        readThread = new ReadThread();
        readThread.start();

        return true;
    }

    protected void disconnect() {
        readThreadRunning = false;
        if (ftDev != null && ftDev.isOpen()) {
            ftDev.close();
        }
    }

    protected void writeBuffer(byte[] buffer) {
        if (ftDev != null && ftDev.isOpen()) {
            ftDev.write(buffer, buffer.length);
        } else {
            Log.e("UsbSerialPort", "Device not open, write failed");
        }
    }

    protected byte[] getBufferData() {
        if (dataCount == 0) return null;
        byte[] data = new byte[dataCount];
        System.arraycopy(readBuffer, 0, data, 0, dataCount);
        dataCount = 0; // reset after read
        return data;
    }

    private class ReadThread extends Thread {
        @Override
        public void run() {
            while (readThreadRunning && ftDev != null && ftDev.isOpen()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {}

                synchronized (ftDev) {
                    int available = ftDev.getQueueStatus();
                    if (available > 0) {
                        byte[] data = new byte[available];
                        ftDev.read(data);
                        System.arraycopy(data, 0, readBuffer, dataCount, available);
                        dataCount += available;
                    }
                }
            }
        }
    }
}

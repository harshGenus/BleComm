package com.genus.usb_comm.usb;


import android.content.Context;
import android.util.Log;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;

import java.util.Objects;

abstract class newSerialPort {
    private static D2xxManager ftD2xx = null;
    private FT_Device ftDev = null;
    private Context _context = null;
    private int _baudRate;
    private byte _dataBits;
    private byte _stopBits;
    private byte _parity;
    private int DevCount = -1;
    private int currentIndex = -1;
    private int openIndex = 0;
    private readThread read_thread;
    private boolean bReadThreadGoing = false;
    private int nCounter = 0;
    public byte[] readData = new byte[4096];

    protected newSerialPort(Context context) {
        this._context = context;
    }

    protected boolean Connect(String comPort, int baudRate) {
        try {
            ftD2xx = D2xxManager.getInstance(this._context);

        } catch (D2xxManager.D2xxException var4) {
            var4.printStackTrace();
        }

        this._baudRate = baudRate;
        this._dataBits = 8;
        this._stopBits = 0;
        this._parity = 0;
        this.SetupD2xxLibrary();
        this.createDeviceList();
        if (!this.connectFunction()) {
            return false;
        } else if (this.DevCount > 0 && this.ftDev != null) {
            if (!this.ftDev.isOpen()) {
                return false;
            } else {
                this.currentIndex = this.openIndex;
                if (!this.ftDev.setBitMode((byte)0, (byte)0)) {
                    return false;
                } else if (!this.ftDev.setBaudRate(this._baudRate)) {
                    return false;
                } else if (!this.ftDev.setDataCharacteristics(this._dataBits, this._stopBits, this._parity)) {
                    return false;
                } else {
                    this.read_thread = new readThread();
                    this.read_thread.start();
                    this.bReadThreadGoing = true;
                    return true;
                }
            }
        } else {
            return false;
        }
    }

    private void SetupD2xxLibrary() {
        if (!ftD2xx.setVIDPID(1027, 44449)) {
            Log.d("SerialPort", "setVIDPID Error");
        }
    }

    private void createDeviceList() {
        int tempDevCount = ftD2xx.createDeviceInfoList(this._context);
        if (tempDevCount > 0) {
            if (this.DevCount != tempDevCount) {
                this.DevCount = tempDevCount;
            }
        } else {
            this.DevCount = -1;
            this.currentIndex = -1;
        }
    }

    private boolean connectFunction() {
        if (this.currentIndex != this.openIndex) {
            if (this.ftDev == null) {
                this.ftDev = ftD2xx.openByIndex(this._context, this.openIndex);
                return true;
            } else {
                synchronized(this.ftDev) {
                    this.ftDev = ftD2xx.openByIndex(this._context, this.openIndex);

                    return false;
                }
            }
        } else {
            return true;
        }
    }

    protected void WriteBuffer(byte[] buffer) {

        int bufferSize = 4096;  // Set your desired buffer size

        if (!this.ftDev.isOpen()) {
            Log.d("SerialPort", "SendMessage: device not open");
        } else {
            this.nCounter = 0;
            this.ftDev.write(buffer, buffer.length);
        }
    }
    public String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];

        for(int j = 0; j < bytes.length; ++j) {
            int v = bytes[j] & 255;
            hexChars[j * 2] = this.hexArray[v >>> 4];
            hexChars[j * 2 + 1] = this.hexArray[v & 15];
        }

        return new String(hexChars);
    }

    private final char[] hexArray = "0123456789ABCDEF".toCharArray();

    protected byte[] GetBufferData() {

        if (this.nCounter == 0) {
            return null;
        } else {
            byte[] data = new byte[this.nCounter];

            for(int i = 0; i < this.nCounter; ++i) {
                data[i] = this.readData[i];
            }
            return data;
        }
    }

    protected void Disconnect() {
        this.DevCount = -1;
        this.currentIndex = -1;
        this.bReadThreadGoing = false;

        try {
            Thread.sleep(50L);
        } catch (InterruptedException var3) {
            var3.printStackTrace();
            Log.d("SerialPort", Objects.requireNonNull(var3.getMessage()));
        }

        if (this.ftDev != null) {
            synchronized(this.ftDev) {
                if (this.ftDev.isOpen()) {
                    this.ftDev.close();
                }
            }
        }

    }

    private class readThread extends Thread {
        readThread() {
            this.setPriority(10);
        }

        public void run() {
            boolean i = false;
            boolean iavailable = false;
            Object var3 = null;

            while(newSerialPort.this.bReadThreadGoing) {
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException var5) {
                    Log.d("SerialPort", Objects.requireNonNull(var5.getMessage()));
                }

                synchronized(newSerialPort.this.ftDev) {
                    int iavailablex = newSerialPort.this.ftDev.getQueueStatus();
                    if (iavailablex > 0) {
                        byte[] data = new byte[iavailablex];
                        newSerialPort.this.ftDev.read(data);

                        for(int ix = 0; ix < iavailablex; ++ix) {
                            newSerialPort.this.readData[newSerialPort.this.nCounter] = data[ix];
                            newSerialPort var10000 = newSerialPort.this;
                            var10000.nCounter = var10000.nCounter + 1;
                        }
                    }
                }
            }

        }
    }
}
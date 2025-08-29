package com.genus.usb_comm.usb;

import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.util.HashMap;

public class UsbComm {
    private static final String TAG = "UsbComm";

    private final Context context;
    private UsbManager usbManager;
    private UsbDevice device;
    private UsbInterface usbInterface;
    private UsbDeviceConnection connection;
    private UsbEndpoint inEndpoint;
    private UsbEndpoint outEndpoint;

    public UsbComm(Context ctx) {
        this.context = ctx;
        this.usbManager = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
    }

    /**
     * Open the first available USB device (you can refine this to filter your RS232 dongle).
     */
    public boolean open() {
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        if (deviceList.isEmpty()) {
            Log.e(TAG, "No USB devices found");
            return false;
        }

        // Pick the first device (for demo) — you should match VendorID/ProductID for yours
        device = deviceList.values().iterator().next();
        Log.i(TAG, "Found USB device: " + device.getDeviceName());

        usbInterface = device.getInterface(0); // most serial adapters have 1 interface
        connection = usbManager.openDevice(device);

        if (connection == null) {
            Log.e(TAG, "Failed to open USB connection");
            return false;
        }

        if (!connection.claimInterface(usbInterface, true)) {
            Log.e(TAG, "Failed to claim interface");
            connection.close();
            return false;
        }

        // Identify endpoints
        for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
            UsbEndpoint ep = usbInterface.getEndpoint(i);
            if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
                    inEndpoint = ep;
                } else if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                    outEndpoint = ep;
                }
            }
        }

        if (inEndpoint == null || outEndpoint == null) {
            Log.e(TAG, "Missing IN/OUT endpoints");
            return false;
        }

        Log.i(TAG, "USB device opened successfully");
        return true;
    }

    /**
     * Read data from USB IN endpoint
     */
    public int read(byte[] buffer) {
        if (connection == null || inEndpoint == null) return -1;
        return connection.bulkTransfer(inEndpoint, buffer, buffer.length, 100); // timeout 100ms
    }

    /**
     * Write data to USB OUT endpoint
     */
    public int write(byte[] data) {
        if (connection == null || outEndpoint == null) return -1;
        return connection.bulkTransfer(outEndpoint, data, data.length, 100); // timeout 100ms
    }

    /**
     * Close connection
     */
    public void close() {
        if (connection != null) {
            connection.releaseInterface(usbInterface);
            connection.close();
            connection = null;
            Log.i(TAG, "USB connection closed");
        }
    }
}

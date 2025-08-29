package com.genus.usb_comm.ble;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattService;

import java.util.List;

public class DiscoveredServices {
    public BluetoothGatt gatt;
    public int status;
    public List<BluetoothGattService> btServices = null;

    public DiscoveredServices() {
    }
}
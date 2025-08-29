package com.genus.usb_comm.ble;

import android.bluetooth.BluetoothDevice;

import java.util.Objects;

public class DeviceItem {
    private String name;
    private String address;
    private BluetoothDevice device;

    public DeviceItem(String name, String address, BluetoothDevice device) {
        this.name = name;
        this.address = address;
        this.device = device;
    }

    public String getName() { return name; }
    public String getAddress() { return address; }
    public BluetoothDevice getDevice() { return device; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeviceItem)) return false;
        DeviceItem that = (DeviceItem) o;
        return Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
    }
}

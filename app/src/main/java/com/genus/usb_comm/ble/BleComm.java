package com.genus.usb_comm.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import java.util.Arrays;
import java.util.UUID;

public class BleComm {
    private static final UUID SIMPLE_SERVICE = UUID.fromString("f000c0e0-0451-4000-b000-000000000000");
    private static final UUID SIMPLE3_DATA = UUID.fromString("f000c0e1-0451-4000-b000-000000000000");
    private BluetoothDevice _device;
    public BluetoothGattCharacteristic characteristic;
    private BluetoothGatt mConnectedGatt;
    public Context _context;
    private BLECommunication _bleComm;
    private DiscoveredServices _discoveredServices = null;
    private final char[] hexArray = "0123456789ABCDEF".toCharArray();

    public BleComm(BluetoothDevice device, Context context) {
        this._device = device;
        this._context = context;

    }


    public boolean ConnectBLE() {
        try {
            this._bleComm = new BLECommunication(this._context, this._device);
            this.mConnectedGatt = this._bleComm.ConnectGatt();
            this._discoveredServices = this._bleComm.DiscoverServices();

            if (this._discoveredServices.btServices != null) {

                BluetoothGattService service = this.mConnectedGatt.getService(SIMPLE_SERVICE);

                if (service == null) {
                    return false;
                }

                this.characteristic = service.getCharacteristic(SIMPLE3_DATA);
                return this._bleComm.SetCharacteristicNotification(this.characteristic, true) &&
                        this._bleComm.WriteDescriptor(
                                this.characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")), /*Client Characteristic Configuration*/
                                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        );
            }
        } catch (Exception e) {
            Log.e("BLE_ERROR", "Error in BLE Connection", e);
        }
        return false;
    }

    public void DisconnectBLE() {
        Log.d("DisconnectBLE", "inside");
        if (this.mConnectedGatt != null) {
            if (this.characteristic != null) {
                this._bleComm.SetCharacteristicNotification(this.characteristic, false);
            }

            this.mConnectedGatt.close();
            this.mConnectedGatt.disconnect();
        }
        this.mConnectedGatt = null;
        this._bleComm = null;
    }

    int count = 0;

    public byte[] DLMSWrite(byte[] buffer, int offset, int length) {
        byte[] arr = Arrays.copyOfRange(buffer, offset, buffer.length);
        Log.d("BLE SEND", this.bytesToHex(arr));
        arr = this._bleComm.DlmsWrite(this.characteristic, arr);

        count = count + 1;
        Log.d(count + " BLE RCV", this.bytesToHex(arr));

        return arr;
    }




    public String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return "";
        } else {
            char[] hexChars = new char[bytes.length * 2];

            for (int j = 0; j < bytes.length; ++j) {
                int v = bytes[j] & 255;
                hexChars[j * 2] = this.hexArray[v >>> 4];
                hexChars[j * 2 + 1] = this.hexArray[v & 15];
            }

            return new String(hexChars);
        }
    }

}
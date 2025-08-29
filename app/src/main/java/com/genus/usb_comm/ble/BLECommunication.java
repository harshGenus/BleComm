package com.genus.usb_comm.ble;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.genus.usb_comm.SavedPreference;

import java.util.Arrays;
import java.util.Calendar;
import java.util.UUID;

public class BLECommunication {
    private static final String TAG = "BleCommunication";

    // Example UUIDs: update to your correct ones
    private static final UUID SERVICE_UUID =
            UUID.fromString("f000c0e0-0451-4000-b000-000000000000");
    private static final UUID CHAR_UUID =
            UUID.fromString("f000c0e1-0451-4000-b000-000000000000");
    private static final UUID CLIENT_CONFIG_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private Context context;
    private BluetoothDevice device;

    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic writeChar;

    private int nCounter = 0;
    private final byte[] rcvBuffer = new byte[2500];

    public BLECommunication(Context context, BluetoothDevice device) {
        this.context = context;
        this.device = device;
    }

    // --- Callbacks ---
    public interface BleListener {
        void onConnected();

        void onDisconnected();

        void onWriteReady();

        void onDataReceived(byte[] data);
    }

    private BleListener listener;

    public void setListener(BleListener l) {
        this.listener = l;
    }

    // --- Connect/Disconnect ---
    public void connect() {
        Log.i(TAG, "Connecting to BLE device: " + device.getAddress());
        gatt = device.connectGatt(context, false, gattCallback);
    }

    public void disconnect() {
        if (gatt != null) {
            gatt.disconnect();
            gatt.close();
        }
        gatt = null;
        writeChar = null;
        if (listener != null) listener.onDisconnected();
    }

    // --- Write method ---
    public boolean write(byte[] buffer) {
        if (gatt == null || writeChar == null) {
            Log.w(TAG, "Not connected or writeChar not ready");
            return false;
        }
        byte[] arr = Arrays.copyOf(buffer, buffer.length);
        Log.d(TAG, "BLE SEND: " + bytesToHex(arr));

        writeChar.setValue(arr);
        writeChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        boolean result = false;
        if (this.gatt != null) {
            result= gatt.writeCharacteristic(writeChar);

        }
        try {
            Thread.sleep(1500L);
        } catch (InterruptedException e) {
            //  throw new RuntimeException(e);
        }
        return result;
    }

    // --- Send with DLMS style (packet + response wait) ---
    public byte[] dlmsWrite(byte[] data) {
        if (writeChar == null) return null;

        this.nCounter = 0;
        Arrays.fill(rcvBuffer, (byte) 0);

        write(data);

        long lastDataTime = Calendar.getInstance().getTimeInMillis();
        long timeout = 3000L;
        long idleGap = 200L;

//        while ((this.nCounter <= 3) &&
//                (Calendar.getInstance().getTimeInMillis() - lastDataTime < idleGap) &&
//                Calendar.getInstance().getTimeInMillis() - (lastDataTime - idleGap) < timeout) {
//            try {
//                Thread.sleep(20L);
//            } catch (InterruptedException ignored) {
//            }
//        }
        long sendTime = Calendar.getInstance().getTimeInMillis();
        do {
            try {
                Thread.sleep(50L);
            } catch (InterruptedException var7) {
                //  Log.d(this.TAG, var7.getMessage());
            }
        } while ((this.nCounter <= 3) && Calendar.getInstance().getTimeInMillis() - sendTime < (long) 1000);

        if (this.nCounter == 0) {
            Log.w(TAG, "No response received within timeout.");
            return null;
        } else {
            byte[] arr = new byte[this.nCounter];
            System.arraycopy(this.rcvBuffer, 0, arr, 0, this.nCounter);
            Log.e(TAG, "BLE response: " + arr.length + " bytes: \n" + bytesToHex(arr));
            return arr;
        }
    }

    // --- Internal GATT callback ---
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected, discovering services...");
                if (listener != null) listener.onConnected();
                gatt.requestMtu(255);
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "Disconnected");
                if (listener != null) listener.onDisconnected();
                writeChar = null;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    writeChar = service.getCharacteristic(CHAR_UUID);
                    if (writeChar != null) {
                        Log.i(TAG, "Write Characteristic ready");
                        enableNotifications();
                        if (listener != null) listener.onWriteReady();
                    } else {
                        Log.e(TAG, "Characteristic not found");
                    }
                } else {
                    Log.e(TAG, "Service not found");
                }
            } else {
                Log.e(TAG, "Service discovery failed: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.e("on read", characteristic.toString());
            if (status != BluetoothGatt.GATT_SUCCESS) return;

            appendBuffer(characteristic.getValue());
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.i(TAG, "Write complete, status=" + status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            byte[] data = characteristic.getValue();
            appendBuffer(data);
            Log.d(TAG, "Notification: " + bytesToHex(data));
            if (listener != null) listener.onDataReceived(data);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            SavedPreference.setMTU(mtu);
            Log.e(TAG, "MTU=" + mtu + ", success=" + (status == BluetoothGatt.GATT_SUCCESS));
        }
    };

    private void enableNotifications() {
        if (gatt == null || writeChar == null) return;

        gatt.setCharacteristicNotification(writeChar, true);
        BluetoothGattDescriptor descriptor = writeChar.getDescriptor(CLIENT_CONFIG_UUID);
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            gatt.writeDescriptor(descriptor);
            Log.i(TAG, "Notifications enabled.");
        }
    }

    private void appendBuffer(byte[] data) {
        if (data != null) {
            for (byte datum : data) {
                this.rcvBuffer[this.nCounter] = datum;
                ++this.nCounter;
                if (this.nCounter == this.rcvBuffer.length) {
                    this.nCounter = 0;
                }
            }
        }
    }

    private final char[] hexArray = "0123456789ABCDEF".toCharArray();

    private String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; ++j) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}

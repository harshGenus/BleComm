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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.genus.usb_comm.MainActivity;
import com.genus.usb_comm.SavedPreference;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Objects;
import java.util.UUID;

public class BLECommunication {
    private static final String TAG = "BleCommunication";
    public DiscoveredServices _discoveredServices;
    private BluetoothGattCharacteristic notifyChar;


    // Example UUIDs: update to your correct ones
    // Example UUIDs: update to your correct ones
    private static final UUID SERVICE_UUID =
            UUID.fromString("f000c0e0-0451-4000-b000-000000000000");

    private static final UUID WRITE_UUID =
            UUID.fromString("f000c0e1-0451-4000-b000-000000000000");

    // notify UUID – you need to confirm from logs
    private static final UUID NOTIFY_UUID =
            UUID.fromString("f000c0e2-0451-4000-b000-000000000000");

    private static final UUID CLIENT_CONFIG_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private Context _context;
    private BluetoothDevice _device;

    private BluetoothGatt _gatt;
    public int _status = -1;
    public int _newState = -1;
    private BluetoothGattCharacteristic writeChar;
    private static final UUID DEVICE_INFORMATION_SERVICE_UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    private static final UUID SERIAL_NUMBER_UUID = UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb");


    private int nCounter = 0;
    private final byte[] rcvBuffer = new byte[2500];

    public BLECommunication(Context context, BluetoothDevice device) {
        this._context = context;
        this._device = device;
        this._discoveredServices = new DiscoveredServices();
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
        Log.i(TAG, "Connecting to BLE device: " + _device.getAddress());
        _gatt = _device.connectGatt(_context, false, _gattCallback);
    }

    public void disconnect() {
        if (_gatt != null) {
            _gatt.disconnect();
            _gatt.close();
        }
        _gatt = null;
        writeChar = null;
        notifyChar = null;
//        if (listener != null) listener.onDisconnected();
    }

    // --- Write method ---
    public boolean write(byte[] buffer) {
        if (_gatt == null || writeChar == null) {
            Log.w(TAG, "Not connected or writeChar not ready");
            return false;
        }
        Log.d(TAG, "BLE SEND: " + bytesToHex(buffer));

        writeChar.setValue(buffer);
        writeChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        boolean result = false;
        if (this._gatt != null) {
            result = _gatt.writeCharacteristic(writeChar);

        }
        try {
            Thread.sleep(1500L);
        } catch (InterruptedException e) {
            //  throw new RuntimeException(e);
        }
        return result;
    }

    private void SendPacket(BluetoothGattCharacteristic characteristic, byte[] data) {
        byte[] initial_packet = new byte[]{3, 0, 0};
        Log.e("data length:", data.length + "");
        int times = 0;

        int updatemtu = SavedPreference.getMTU() - 3;
        times = Byte.parseByte(String.valueOf(data.length / updatemtu));

        Log.e("times:", times + "");
        byte[] sending_continue_hex = new byte[updatemtu];
        for (int time = 0; time <= times; ++time) {

            int character_length;
            byte[] sending_last_hex;

            if (time == times) {
                character_length = data.length - updatemtu * times;
                initial_packet[1] = Byte.parseByte(String.valueOf(character_length + 3));
                initial_packet[2] = 0;
                sending_last_hex = new byte[character_length];
                System.arraycopy(data, sending_continue_hex.length * time, sending_last_hex, 0, sending_last_hex.length);

                byte[] last_packet = new byte[character_length + 3];
                System.arraycopy(initial_packet, 0, last_packet, 0, initial_packet.length);
                System.arraycopy(sending_last_hex, 0, last_packet, initial_packet.length, sending_last_hex.length);
                //  Log.e("initial_packet:22", bytesToHex(sending_continue_hex));
                characteristic.setValue(sending_last_hex);
            } else {
                character_length = sending_continue_hex.length;
                initial_packet[1] = Byte.parseByte(String.valueOf(character_length + 3));
                initial_packet[2] = 1;

                System.arraycopy(data, sending_continue_hex.length * time, sending_continue_hex, 0, sending_continue_hex.length);
                sending_last_hex = new byte[character_length + 3];

                System.arraycopy(initial_packet, 0, sending_last_hex, 0, initial_packet.length);
                System.arraycopy(sending_continue_hex, 0, sending_last_hex, initial_packet.length, sending_continue_hex.length);
                Log.e("initial_packet:", bytesToHex(sending_continue_hex));
                characteristic.setValue(sending_continue_hex);

            }

            if (ActivityCompat.checkSelfPermission(_context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

            }
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            if (this._gatt != null) {
                _gatt.writeCharacteristic(characteristic);

            }
            try {
                Thread.sleep(1500L);
            } catch (InterruptedException e) {
                //  throw new RuntimeException(e);
            }

        }

    }

    // --- Send with DLMS style (packet + response wait) ---
    public byte[] DlmsWrite(BluetoothGattCharacteristic characteristic, byte[] data) {

        if (characteristic == null) {
            return null;
        } else {
            this.nCounter = 0;
            this.rcvBuffer[1] = 0;
            this.rcvBuffer[2] = 0;
            this.SendPacket(characteristic, data);
            long sendTime = Calendar.getInstance().getTimeInMillis();
            do {
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException var7) {
                    //  Log.d(this.TAG, var7.getMessage());
                }
            } while ((this.nCounter <= 3) && Calendar.getInstance().getTimeInMillis() - sendTime < (long) 1000);
//            while ((this.nCounter <= 3 || Integer.parseInt(String.format("%02X", this.rcvBuffer[1] & 7) + String.format("%02X", this.rcvBuffer[2]), 16) + 2 < this.nCounter) && Calendar.getInstance().getTimeInMillis() - sendTime < (long)1000 );//this.wait

            if (this.nCounter == 0) {
                return null;
            } else {
                byte[] arr = new byte[this.nCounter];

                System.arraycopy(this.rcvBuffer, 0, arr, 0, this.nCounter);

                return arr;
            }
        }
    }

    // --- Internal GATT callback ---
    public BluetoothGattCallback _gattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            boolean isSuccess = status == BluetoothGatt.GATT_SUCCESS;
            boolean isConnected = newState == BluetoothProfile.STATE_CONNECTED;
            boolean isDisConnected = newState == BluetoothProfile.STATE_DISCONNECTED;
            if (status == 8) {
                SavedPreference.setBleStatus(status);
                Log.e("BLE", "Disconnected");
            }

            if (newState == BluetoothGatt.GATT_FAILURE) {
                Log.e("Connected3", "nooooo");
                Toast.makeText(_context, "You are trying to connect to a Bluetooth device too many times in too short time", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.");
                _gatt = gatt;
                _status = status;
                _newState = newState;
                _gatt.requestMtu(255);
                _gatt.discoverServices();
                notifyConnected();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
                if (_gatt != null) {
                    _gatt.close();
                    _gatt = null;
                }
                notifyDisconnected();
            } else {
                Log.w(TAG, "BLE Connection state changed: status=" + status + " newState=" + newState);
            }

            if (isConnected) {
                Log.e("Connected", "YEs");
                _gatt = gatt;
                _status = status;
                _newState = newState;
                if (ActivityCompat.checkSelfPermission(_context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

                }
                _gatt.requestMtu(255);
                _gatt.discoverServices();
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } else if (isSuccess && isDisConnected) {
                _gatt.disconnect();
                _gatt.close();
                _gatt = null;
                Log.e("Connected1", "nooooo");

            } else if (status == 133 && newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.e("Connected2", "nooooo");
                ConnectGatt();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            if (status == BluetoothGatt.GATT_SUCCESS) {
                _discoveredServices.status = status;
                _discoveredServices.btServices = gatt.getServices();
                _discoveredServices.gatt = gatt;

                for (BluetoothGattService service : gatt.getServices()) {
                    Log.i(TAG, "Service: " + service.getUuid());
                    for (BluetoothGattCharacteristic ch : service.getCharacteristics()) {
                        int props = ch.getProperties();
                        StringBuilder sb = new StringBuilder();
                        if ((props & BluetoothGattCharacteristic.PROPERTY_READ) > 0)
                            sb.append(" READ");
                        if ((props & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0)
                            sb.append(" WRITE");
                        if ((props & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0)
                            sb.append(" WRITE_NO_RESP");
                        if ((props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0)
                            sb.append(" NOTIFY");
                        if ((props & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0)
                            sb.append(" INDICATE");
                        Log.i(TAG, "  Char: " + ch.getUuid() + " Props:" + sb);
                    }
                }

                BluetoothGattService customService = gatt.getService(SERVICE_UUID);
                if (customService == null) {
                    Log.e(TAG, "Custom service not found: " + SERVICE_UUID);
                    return;
                }
                BluetoothGattCharacteristic c0e1 = customService.getCharacteristic(WRITE_UUID);

                if (c0e1 != null) {
                    int props = c0e1.getProperties();
                    writeChar = c0e1;
                    Log.i(TAG, "Write characteristic found: " + WRITE_UUID);

                    if ((props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                        notifyChar = c0e1;
                        Log.i(TAG, "Same characteristic also supports NOTIFY, enabling it...");
                        enableNotifications(notifyChar);
                    }
                } else {
                    Log.e(TAG, "Characteristic f000c0e1 not found at all!");
                }

                if (listener != null) listener.onWriteReady();
//                // (Optional) read serial number as you had before
//                BluetoothGattService disService = gatt.getService(DEVICE_INFORMATION_SERVICE_UUID);
//                if (disService != null) {
//                    BluetoothGattCharacteristic serialChar = disService.getCharacteristic(SERIAL_NUMBER_UUID);
//                    if (serialChar != null) {
//                        gatt.readCharacteristic(serialChar);
//                    }
//                }
//                if (listener != null) listener.onWriteReady();
            }
        }

        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.e("on read", characteristic.toString());
            if (status != BluetoothGatt.GATT_SUCCESS) return;
            notifyDataReceived(characteristic.getValue());
            if (SERIAL_NUMBER_UUID.equals(characteristic.getUuid())) {
                String serialValue = characteristic.getStringValue(0);
                Log.d("BLE", "Serial Number: " + serialValue);
            }
            appendBuffer(characteristic.getValue());
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Write successful: " + bytesToHex(characteristic.getValue()));
            } else {
                Log.e(TAG, "Write failed with status: " + status);
            }
        }

        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        }

        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        }

        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

            gatt = gatt;
            Log.i(TAG, "Notification from: " + characteristic.getUuid()
                    + " Value: " + bytesToHex(characteristic.getValue()));
            notifyDataReceived(characteristic.getValue());
            appendBuffer(characteristic.getValue());
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            SavedPreference.setMTU(mtu);
            Log.e("MTU0->", mtu + " , Succeed: " + (status == BluetoothGatt.GATT_SUCCESS));
        }
    };

    public DiscoveredServices DiscoverServices() {
        long sendTime = Calendar.getInstance().getTimeInMillis();

        do {
            try {
                Thread.sleep(50L);
            } catch (InterruptedException var4) {
                Log.d(this.TAG, Objects.requireNonNull(var4.getMessage()));
            }
        } while ((this._discoveredServices.btServices == null || this._discoveredServices.btServices.isEmpty()) && Calendar.getInstance().getTimeInMillis() - sendTime < (long) 10000L);

        return this._discoveredServices;
    }

    private void enableNotifications(BluetoothGattCharacteristic ch) {
        if (_gatt == null || ch == null) return;

        _gatt.setCharacteristicNotification(ch, true);
        BluetoothGattDescriptor descriptor = ch.getDescriptor(CLIENT_CONFIG_UUID);
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            _gatt.writeDescriptor(descriptor);
            Log.i(TAG, "Notifications enabled for " + ch.getUuid());
        }
    }


    public boolean SetCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enable) {
        synchronized (this._gatt) {
            boolean var10000;

            if (this._gatt == null) {
                return false;
            }

            if (characteristic == null) {
                return false;
            }

            int properties = characteristic.getProperties();
            if ((properties & 16) != 16) {
                return false;
            }

            if (ActivityCompat.checkSelfPermission(_context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return true;
            }
            var10000 = this._gatt.setCharacteristicNotification(characteristic, enable);

            return var10000;
        }
    }

    public boolean WriteDescriptor(BluetoothGattDescriptor descriptor, byte[] descriptorSetValue) {
        descriptor.setValue(descriptorSetValue);
        if (ActivityCompat.checkSelfPermission(_context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

            return true;
        }
        return this._gatt.writeDescriptor(descriptor);
    }

    public BluetoothGatt ConnectGatt() {
//        refreshDeviceCache();
        if (ActivityCompat.checkSelfPermission(_context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

        }
        _gatt = _device.connectGatt(_context, false, _gattCallback);


        long sendTime = Calendar.getInstance().getTimeInMillis();

        do {
            try {
                Thread.sleep(50L);
            } catch (InterruptedException var4) {
                Log.d(this.TAG, Objects.requireNonNull(var4.getMessage()));
            }
        } while (this._status == 0 && this._newState == 2 && Calendar.getInstance().getTimeInMillis() - sendTime < (long) 1000L);
        return this._gatt;
    }

    private void appendBuffer(byte[] data) {
        Log.e("byte data", Arrays.toString(data));
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

    private void notifyConnected() {
        if (listener != null) listener.onConnected();
        Intent intent = new Intent(MainActivity.ConnectionActions.ACTION_BLE_CONNECTED);
        _context.sendBroadcast(intent);
    }

    private void notifyDisconnected() {
        if (listener != null) listener.onDisconnected();
        Intent intent = new Intent(MainActivity.ConnectionActions.ACTION_BLE_DISCONNECTED);
        _context.sendBroadcast(intent);
    }

    private void notifyDataReceived(byte[] data) {
        if (listener != null) listener.onDataReceived(data);
        Intent intent = new Intent(MainActivity.ConnectionActions.ACTION_BLE_DATA);
        intent.putExtra("payload", data);
        _context.sendBroadcast(intent);
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

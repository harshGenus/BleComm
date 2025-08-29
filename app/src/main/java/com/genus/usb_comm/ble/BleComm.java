//package com.genus.usb_comm.ble;
//
//import android.bluetooth.BluetoothDevice;
//import android.bluetooth.BluetoothGatt;
//import android.bluetooth.BluetoothGattCallback;
//import android.bluetooth.BluetoothGattCharacteristic;
//import android.bluetooth.BluetoothGattDescriptor;
//import android.bluetooth.BluetoothGattService;
//import android.content.Context;
//import android.util.Log;
//
//import java.util.Arrays;
//import java.util.UUID;
//
//public class BleComm {
//    private static final String TAG = "BleComm";
//
//    // Custom service & characteristic UUIDs
//    private static final UUID SERVICE_UUID =
//            UUID.fromString("f000c0e0-0451-4000-b000-000000000000");
//    private static final UUID CHAR_UUID =
//            UUID.fromString("f000c0e1-0451-4000-b000-000000000000");
//    private static final UUID CLIENT_CONFIG_UUID =
//            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
//
//    private final Context context;
//
//    private final BluetoothDevice device;
//
//    private BluetoothGatt gatt;
//    private BluetoothGattCharacteristic characteristic;
//
//    public BleComm(Context context, BluetoothDevice device) {
//        this.context = context;
//        this.device = device;
//    }
//
//    public boolean connect() {
//        try {
//            Log.i(TAG, "Connecting to BLE device: " + device.getAddress());
//            gatt = device.connectGatt(context, false, gattCallback);
//            return true;
//        } catch (Exception e) {
//            Log.e(TAG, "BLE connect error", e);
//            return false;
//        }
//    }
//
//    public void disconnect() {
//        if (gatt != null) {
//            Log.i(TAG, "Disconnecting BLE...");
//            gatt.disconnect();
//            gatt.close();
//        }
//        gatt = null;
//        characteristic = null;
//    }
//
//    public interface BleReadCallback {
//        void onDataReceived(byte[] data);
//    }
//
//    private BleReadCallback bleReadCallback;
//
//    public void setBleReadCallback(BleReadCallback cb) {
//        this.bleReadCallback = cb;
//    }
//
//
//    public boolean write(byte[] buffer) {
//        if (gatt == null || characteristic == null) {
//            Log.w(TAG, "Not connected, cannot write");
//            return false;
//        }
//        byte[] arr = Arrays.copyOf(buffer, buffer.length);
//        Log.d(TAG, "BLE SEND: " + bytesToHex(arr));
//
//        characteristic.setValue(arr);
//        boolean success = gatt.writeCharacteristic(characteristic);
//        Log.d(TAG, "Write result: " + success);
//        return success;
//    }
//
//    // ---- GATT CALLBACK ----
//    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
//        @Override
//        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
//            Log.i(TAG, "Connection state changed: " + status + " -> " + newState);
//            if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
//                Log.i(TAG, "Connected, discovering services...");
//                gatt.discoverServices();
//            } else if (newState == android.bluetooth.BluetoothProfile.STATE_DISCONNECTED) {
//                Log.w(TAG, "Disconnected from GATT server.");
//                characteristic = null;
//            }
//        }
//
//        @Override
//        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
//            if (status == BluetoothGatt.GATT_SUCCESS) {
//                BluetoothGattService service = gatt.getService(SERVICE_UUID);
//                if (service != null) {
//                    characteristic = service.getCharacteristic(CHAR_UUID);
//                    if (characteristic != null) {
//                        Log.i(TAG, "Characteristic found: ready for write.");
//                        // enable notifications if needed
//                        enableNotifications();
//                    } else {
//                        Log.e(TAG, "Characteristic not found: " + CHAR_UUID);
//                    }
//                } else {
//                    Log.e(TAG, "Service not found: " + SERVICE_UUID);
//                }
//            } else {
//                Log.e(TAG, "Service discovery failed, status=" + status);
//            }
//        }
//
//        @Override
//        public void onCharacteristicWrite(BluetoothGatt gatt,
//                                          BluetoothGattCharacteristic characteristic,
//                                          int status) {
//            Log.i(TAG, "onCharacteristicWrite status=" + status);
//        }
//
//        @Override
//        public void onCharacteristicChanged(BluetoothGatt gatt,
//                                            BluetoothGattCharacteristic characteristic) {
//            if (bleReadCallback != null) {
//                bleReadCallback.onDataReceived(characteristic.getValue());
//            }
//            byte[] data = characteristic.getValue();
//            Log.d(TAG, "Notification received: " + bytesToHex(data));
//        }
//    };
//
//    private void enableNotifications() {
//        if (gatt == null || characteristic == null) return;
//
//        gatt.setCharacteristicNotification(characteristic, true);
//        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CONFIG_UUID);
//        if (descriptor != null) {
//            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
//            gatt.writeDescriptor(descriptor);
//            Log.i(TAG, "Notifications enabled.");
//        }
//    }
//
//    private final char[] hexArray = "0123456789ABCDEF".toCharArray();
//
//    private String bytesToHex(byte[] bytes) {
//        if (bytes == null) return "";
//        char[] hexChars = new char[bytes.length * 2];
//        for (int j = 0; j < bytes.length; ++j) {
//            int v = bytes[j] & 0xFF;
//            hexChars[j * 2] = hexArray[v >>> 4];
//            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
//        }
//        return new String(hexChars);
//    }
//}

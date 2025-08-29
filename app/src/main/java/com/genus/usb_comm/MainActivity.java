package com.genus.usb_comm;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.genus.usb_comm.ble.BLECommunication;
import com.genus.usb_comm.usb.UsbSerialCommunication;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSIONS_CODE = 1001;

    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private TextView tvStatus, tvUsbStatus, tvBleStatus;
    private View usbStatusIndicator, bleStatusIndicator;
    private Button btnScanBle, btnToggleConnect, btnSendData;
    private EditText etBleName;
    private ScrollView statusScrollView;

    private UsbSerialCommunication usbComm;
    private BLECommunication bleCommunication;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;

    private boolean usbConnected = false;
    private boolean bleConnected = false;
    private BluetoothDevice connectedDevice;

    // ---------------- Lifecycle ----------------
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkAndRequestPermissions();

        tvStatus = findViewById(R.id.tvStatus);
        statusScrollView = findViewById(R.id.statusScrollView);
        tvUsbStatus = findViewById(R.id.tvUsbStatus);
        tvBleStatus = findViewById(R.id.tvBleStatus);
        usbStatusIndicator = findViewById(R.id.usbStatusIndicator);
        bleStatusIndicator = findViewById(R.id.bleStatusIndicator);
        etBleName = findViewById(R.id.etBleName);

        btnScanBle = findViewById(R.id.btnScanBle);
        btnToggleConnect = findViewById(R.id.btnToggleConnect);
        btnSendData = findViewById(R.id.btnSendData);
        //    btnSendData.setEnabled(false); // disabled until writeChar ready

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }

        // Init USB comm
        usbComm = new UsbSerialCommunication(this);

        // Button actions
        btnScanBle.setOnClickListener(v -> startBleScan());

        btnToggleConnect.setOnClickListener(v -> {
            if (!usbConnected) {
                connectUsb();
            } else {
                disconnectUsb();
            }
        });

        btnSendData.setOnClickListener(v -> sendBleCommand());
//        btnSendData.setOnClickListener(v -> sendUsbCommand());
    }

    private void sendUsbCommand() {
        if (usbConnected) {
            String command = etBleName.getText().toString().trim();
            usbComm.write(command);
        }
    }

    // ---------------- BLE ----------------
    private void startBleScan() {
        String targetName = etBleName.getText().toString().trim();
        if (targetName.isEmpty()) {
            Toast.makeText(this, "Enter BLE device name", Toast.LENGTH_SHORT).show();
            return;
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }

        appendLog("Scanning for BLE: " + targetName);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        bluetoothLeScanner.startScan(new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                if (device == null || device.getName() == null) return;

                Log.e("Found BLE device: ", device.getName());

                if (device.getName().equalsIgnoreCase(targetName)) {
                    bluetoothLeScanner.stopScan(this);
                    appendLog("Found BLE device: " + device.getName());

                    connectedDevice = device;
                    bleCommunication = new BLECommunication(MainActivity.this, connectedDevice);
                    bleCommunication.setListener(new BLECommunication.BleListener() {
                        @Override
                        public void onConnected() {
                            bleConnected = true;
                            updateIndicator(bleStatusIndicator, tvBleStatus, true, "BLE: Connected (" + device.getName() + ")");
                            appendLog("BLE Connected");
                        }

                        @Override
                        public void onDisconnected() {
                            bleConnected = false;
                            updateIndicator(bleStatusIndicator, tvBleStatus, false, "BLE: Disconnected");
                            appendLog("BLE Disconnected");

                            // Auto reconnect feature
                            appendLog("Retrying BLE reconnect...");
                            if (connectedDevice != null) {
                                bleCommunication = new BLECommunication(MainActivity.this, connectedDevice);
                                bleCommunication.setListener(this); // reuse same listener
                                bleCommunication.connect();
                            }
                        }

                        @Override
                        public void onWriteReady() {
                            appendLog("Write characteristic ready!");
//                            btnSendData.setEnabled(true);
                        }

                        @Override
                        public void onDataReceived(byte[] data) {
                            appendLog("Data received: " + bytesToHex(data));
                        }
                    });
                    bleCommunication.connect();
                }
            }
        });
    }

    private void sendBleCommand() {
        if (bleCommunication == null || !bleConnected) {
            appendLog("BLE not connected. Please scan and connect first.");
            Toast.makeText(this, "BLE not connected", Toast.LENGTH_SHORT).show();
            return;
        }

        byte[] command = new byte[]{
                0x43, 0x4D, 0x44, 0x02, 0x0E, 0x00, 0x00, 0x00,
                0x64, 0x00, (byte) 0xD8, (byte) 0xD2, 0x0D, 0x0A
        };

        final ProgressDialog progressDialog = new ProgressDialog(MainActivity.this);
        progressDialog.setTitle("Sending Data...");
        progressDialog.setMessage("Please wait...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        Log.e("sending data",bytesToHex(command));
        appendLog("Sending to BLE: " + bytesToHex(command));
        new Thread(() -> {
            byte[] response = bleCommunication.dlmsWrite(command);

            runOnUiThread(() -> {
                progressDialog.dismiss();
                if (response != null) {
                    appendLog("BLE Response: " + bytesToHex(response));
                } else {
                    appendLog("No response from BLE");
                }
            });
        }).start();
    }

    // ---------------- USB ----------------
    private void connectUsb() {
        boolean ok = usbComm.connect();
        if (ok) {
            usbConnected = true;
            btnToggleConnect.setText("Disconnect USB");
            updateIndicator(usbStatusIndicator, tvUsbStatus, true, "USB: Connected");
            appendLog("FTDI connected!");
        } else {
            appendLog("FTDI connect failed");
        }
    }

    private void disconnectUsb() {
        usbComm.disconnect();
        usbConnected = false;
        btnToggleConnect.setText("Connect USB");
        updateIndicator(usbStatusIndicator, tvUsbStatus, false, "USB: Disconnected");
        appendLog("FTDI disconnected");
    }

    private void sendUsbData(String msg) {
        if (usbComm.write(msg)) {
            appendLog("USB Sent: " + msg);
        } else {
            appendLog("USB Send failed");
        }
    }

    // ---------------- UI Helpers ----------------
    private void appendLog(String msg) {
        runOnUiThread(() -> {
            tvStatus.append("\n" + msg);
            statusScrollView.post(() -> statusScrollView.fullScroll(View.FOCUS_DOWN));
            Log.d(TAG, msg);
        });
    }

    private String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; ++j) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private void updateIndicator(View indicator, TextView label, boolean connected, String text) {
        int drawable = connected ? R.drawable.status_circle_green : R.drawable.status_circle_red;
        indicator.setBackgroundResource(drawable);
        label.setText(text);
    }

    // ---------------- Permissions ----------------
    private void checkAndRequestPermissions() {
        List<String> missingPermissions = new ArrayList<>();
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    missingPermissions.toArray(new String[0]),
                    REQUEST_PERMISSIONS_CODE
            );
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (usbComm != null) usbComm.disconnect();
        if (bleCommunication != null) bleCommunication.disconnect();
    }
}

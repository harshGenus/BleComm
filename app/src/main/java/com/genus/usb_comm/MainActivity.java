package com.genus.usb_comm;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.genus.usb_comm.adapter.LogAdapter;
import com.genus.usb_comm.ble.BLECommunication;
import com.genus.usb_comm.ble.BleComm;
import com.genus.usb_comm.model.LogItem;
import com.genus.usb_comm.usb.UsbSerialCommunication;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_ENABLE_BT = 1;
    Context context;

    private static final int REQUEST_PERMISSIONS_CODE = 1001;

    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION
    };
    RecyclerView rvLogs;
    private TextView tvUsbStatus, tvBleStatus;
    private View usbStatusIndicator, bleStatusIndicator;
    private Button btnScanBle, btnToggleConnect, btnSendData;
    private EditText etBleName;
//    private ScrollView statusScrollView;

    private UsbSerialCommunication usbComm;
    private BLECommunication bleCommunication;
    private Button btnRefreshScreen;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;

    private boolean usbConnected = false;
    LogAdapter logAdapter;
    private boolean bleConnected = false;
    private BluetoothDevice connectedDevice;
    private final BroadcastReceiver connectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ConnectionActions.ACTION_USB_CONNECTED.equals(action)) {
                usbConnected = true;
                updateIndicator(usbStatusIndicator, tvUsbStatus, true, "USB: Connected");
                appendLog("USB connected (via broadcast)", LogItem.Source.OTHER);
            } else if (ConnectionActions.ACTION_USB_DISCONNECTED.equals(action)) {
                usbConnected = false;
                updateIndicator(usbStatusIndicator, tvUsbStatus, false, "USB: Disconnected");
                appendLog("USB disconnected (via broadcast)", LogItem.Source.OTHER);
            } else if (ConnectionActions.ACTION_BLE_CONNECTED.equals(action)) {
                bleConnected = true;
                updateIndicator(bleStatusIndicator, tvBleStatus, true, "BLE: Connected");
                appendLog("BLE connected (via broadcast)", LogItem.Source.OTHER);
            } else if (ConnectionActions.ACTION_BLE_DISCONNECTED.equals(action)) {
                bleConnected = false;
                updateIndicator(bleStatusIndicator, tvBleStatus, false, "BLE: Disconnected");
                appendLog("BLE disconnected (via broadcast)", LogItem.Source.OTHER);
            } else if (ConnectionActions.ACTION_BLE_DATA.equals(action)) {
                byte[] data = intent.getByteArrayExtra("payload");
                appendLog("BLE Data: " + Arrays.toString(data), LogItem.Source.BLE);
            }
        }
    };

    // ---------------- Lifecycle ----------------
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = MainActivity.this;
        SavedPreference.initPref(this);

        checkAndRequestPermissions();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectionActions.ACTION_USB_CONNECTED);
        filter.addAction(ConnectionActions.ACTION_USB_DISCONNECTED);
        filter.addAction(ConnectionActions.ACTION_BLE_CONNECTED);
        filter.addAction(ConnectionActions.ACTION_BLE_DATA);
        filter.addAction(ConnectionActions.ACTION_BLE_DISCONNECTED);
        registerReceiver(connectionReceiver, filter, RECEIVER_NOT_EXPORTED);


//        tvStatus = findViewById(R.id.tvStatus);
//        statusScrollView = findViewById(R.id.statusScrollView);
        tvUsbStatus = findViewById(R.id.tvUsbStatus);
        tvBleStatus = findViewById(R.id.tvBleStatus);
        usbStatusIndicator = findViewById(R.id.usbStatusIndicator);
        bleStatusIndicator = findViewById(R.id.bleStatusIndicator);
        etBleName = findViewById(R.id.etBleName);
        rvLogs = findViewById(R.id.rvLogs);
        logAdapter = new LogAdapter();
        rvLogs.setAdapter(logAdapter);
        rvLogs.setLayoutManager(new LinearLayoutManager(this));
        btnRefreshScreen = findViewById(R.id.refreshScreen);
        btnScanBle = findViewById(R.id.btnScanBle);
        btnToggleConnect = findViewById(R.id.btnToggleConnect);
        btnSendData = findViewById(R.id.btnSendData);

        Spinner spBaudRate = findViewById(R.id.spBaudRate);
        int savedBaudRate = SavedPreference.getBaudRate();
        if (savedBaudRate != 0) {
            // Find the matching index in your array
            String[] baudRates = getResources().getStringArray(R.array.baudrates);
            for (int i = 0; i < baudRates.length; i++) {
                if (Integer.parseInt(baudRates[i]) == savedBaudRate) {
                    spBaudRate.setSelection(i);
                    break;
                }
            }
        }

        spBaudRate.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedValue = parent.getItemAtPosition(position).toString();
                int baudRate = Integer.parseInt(selectedValue);

                spBaudRate.setSelection(position);
                // Save to preferences
                SavedPreference.setBaudRate(baudRate);
                Log.d("Spinner", "BaudRate saved: " + baudRate);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
        btnRefreshScreen.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        });
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
            usbComm.write(command.getBytes());
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

        appendLog("Scanning for BLE: " + targetName, LogItem.Source.OTHER);
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
                    appendLog("Found BLE device: " + device.getName(), LogItem.Source.OTHER);

                    connectedDevice = device;
                    bleCommunication = new BLECommunication(MainActivity.this, connectedDevice);
                    comm = new BleComm(connectedDevice, MainActivity.this);
                    comm.ConnectBLE();
                    bleCommunication.connect();
                    bleCommunication.setListener(new BLECommunication.BleListener() {
                        @Override
                        public void onConnected() {
                            bleConnected = true;
                            updateIndicator(bleStatusIndicator, tvBleStatus, true, "BLE: Connected (" + device.getName() + ")");
                            appendLog("BLE Connected", LogItem.Source.OTHER);
                        }

                        @Override
                        public void onDisconnected() {
                            bleConnected = false;
                            updateIndicator(bleStatusIndicator, tvBleStatus, false, "BLE: Disconnected");
                            appendLog("BLE Disconnected", LogItem.Source.OTHER);

                            // Auto reconnect feature
                            appendLog("Retrying BLE reconnect...", LogItem.Source.OTHER);
                            if (connectedDevice != null) {
                                bleCommunication = new BLECommunication(MainActivity.this, connectedDevice);
                                bleCommunication.setListener(this); // reuse same listener
                                bleCommunication.connect();
                            }
                        }

                        @Override
                        public void onWriteReady() {
                            appendLog("Write characteristic ready!", LogItem.Source.OTHER);
//                            btnSendData.setEnabled(true);
                        }

                        @Override
                        public void onDataReceived(byte[] data) {
                            appendLog("BLE → USB: " + bytesToHex(data), LogItem.Source.BLE);
                            if (usbComm != null) {
                                usbComm.write(data);
                            }
                        }
                    });

                }
            }
        });
    }

    private void sendBleCommand() {
        if (bleCommunication == null || !bleConnected) {
            Log.e(TAG, "BLE not connected. Please scan and connect first." + bleCommunication + " bleConnected " + bleConnected);
            appendLog("BLE not connected. Please scan and connect first.", LogItem.Source.OTHER);
            Toast.makeText(this, "BLE not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        // 43 4D 44 02 0E 00 00 00 64 00 D8 D2 0D 0A
        byte[] command = new byte[]{
                0x43, 0x4D, 0x44, 0x02, 0x0E, 0x00, 0x00, 0x00,
                0x64, 0x00, (byte) 0xD8, (byte) 0xD2, 0x0D, 0x0A
        };

        final ProgressDialog progressDialog = new ProgressDialog(MainActivity.this);
        progressDialog.setTitle("Sending Data...");
        progressDialog.setMessage("Please wait...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        Log.e("sending data", bytesToHex(command));
        appendLog("Sending to BLE: " + bytesToHex(command), LogItem.Source.BLE);


        new Thread(() -> {
            byte[] response = comm.DLMSWrite(command, 0, command.length);

            runOnUiThread(() -> {
                progressDialog.dismiss();
                if (response != null) {
                    appendLog("BLE Response: " + bytesToHex(response), LogItem.Source.BLE);
                } else {
                    appendLog("No response from BLE", LogItem.Source.OTHER);
                }
            });
        }).start();
    }

    int count = 0;
    BleComm comm;

    public static class ConnectionActions {
        public static final String ACTION_USB_CONNECTED = "com.genus.usb_comm.USB_CONNECTED";
        public static final String ACTION_USB_DISCONNECTED = "com.genus.usb_comm.USB_DISCONNECTED";
        public static final String ACTION_BLE_CONNECTED = "com.genus.usb_comm.BLE_CONNECTED";
        public static final String ACTION_BLE_DATA = "com.genus.usb_comm.BLE_DATA";
        public static final String ACTION_BLE_DISCONNECTED = "com.genus.usb_comm.BLE_DISCONNECTED";

    }

    private void disconnectUsb() {
        usbComm.disconnect();
        usbConnected = false;
        btnToggleConnect.setText("Connect USB");
        updateIndicator(usbStatusIndicator, tvUsbStatus, false, "USB: Disconnected");
        appendLog("FTDI disconnected", LogItem.Source.OTHER);
    }

    private void connectUsb() {
        boolean ok = usbComm.connect();
        if (ok) {
            usbConnected = true;
            usbComm.setUsbReadCallback(data -> {
                appendLog("USB → BLE: " + bytesToHex(data), LogItem.Source.USB);
                if (bleCommunication != null && bleConnected) {
                    bleCommunication.writeLarge(data);
                }
            });
            btnToggleConnect.setText("Disconnect USB");
            updateIndicator(usbStatusIndicator, tvUsbStatus, true, "USB: Connected");
            appendLog("FTDI connected!", LogItem.Source.OTHER);
        } else {
            appendLog("FTDI connect failed", LogItem.Source.OTHER);
        }
    }

    private SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    private void appendLog(String message, LogItem.Source source) {
        runOnUiThread(() -> {
            String currentTime = sdf.format(new Timestamp(System.currentTimeMillis()));
            final String fullMessage = currentTime + " : " + message + "\n";
            Log.d(TAG, fullMessage);
            logAdapter.addLog(new LogItem(fullMessage, source));
            rvLogs.smoothScrollToPosition(logAdapter.getItemCount() - 1);
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
        try {
            unregisterReceiver(connectionReceiver);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Receiver already unregistered", e);
        }

        if (usbComm != null) usbComm.disconnect();
        if (bleCommunication != null) bleCommunication.disconnect();
    }
}

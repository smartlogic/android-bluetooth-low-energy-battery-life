package io.smartlogic.bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import java.util.UUID;

public class MainActivity extends Activity {
    private final static String TAG = "MainActivity";
    private final int REQUEST_ENABLE_BT = 1;
    private final int SCAN_PERIOD = 10000;

    private final static UUID BATTERY_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    private final static UUID BATTERY_LEVEL = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");

    private Handler mHandler;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothAdapter.LeScanCallback mScanCallback;
    private BluetoothDevice mDevice;

    private TextView mDeviceName;
    private TextView mBatteryLevel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDeviceName = (TextView) findViewById(R.id.device_name);
        mBatteryLevel = (TextView) findViewById(R.id.battery_level);

        mHandler = new Handler();

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            startScanning();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                Log.d(TAG, "Result");
                startScanning();
        }
    }

    private void startScanning() {
        mScanCallback = new BluetoothAdapter.LeScanCallback() {
            @Override
            public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                Log.d(TAG, device.getName());

                mDevice = device;
                mBluetoothAdapter.stopLeScan(mScanCallback);

                new BluetoothTask(MainActivity.this).execute();
            }
        };
        mBluetoothAdapter.startLeScan(mScanCallback);

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mBluetoothAdapter.stopLeScan(mScanCallback);
            }
        }, SCAN_PERIOD);

    }

    private class BluetoothTask extends AsyncTask<Void, Void, Void> {
        private final Context mContext;

        private BluetoothGatt mBluetoothGatt;
        private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);

                Log.d(TAG, "Bluetooth status: " + status);

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "Connected to GATT server.");
                    Log.i(TAG, "Attempting to start service discovery:" + mBluetoothGatt.discoverServices());

                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "Disconnected from GATT server.");
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);

                Log.d(TAG, "Services discovered status: " + status);

                for (BluetoothGattService service : gatt.getServices()) {
                    if (service.getUuid().equals(BATTERY_UUID)) {
                        Log.d(TAG, String.valueOf(service.getUuid()));

                        BluetoothGattCharacteristic characteristic = service.getCharacteristic(BATTERY_LEVEL);

                        if (characteristic != null) {
                            mBluetoothGatt.readCharacteristic(characteristic);
                        }
                    }
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicRead(gatt, characteristic, status);

                final Integer batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);

                if (batteryLevel != null) {
                    Log.d(TAG, "battery level: " + batteryLevel);

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mBatteryLevel.setText(String.format(getString(R.string.battery_level), batteryLevel.toString()));
                        }
                    });
                }
            }
        };

        public BluetoothTask(Context context) {
            this.mContext = context;
        }

        @Override
        protected Void doInBackground(Void... params) {
            mBluetoothGatt = mDevice.connectGatt(mContext, false, mGattCallback);

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            mDeviceName.setText(String.format(getString(R.string.connected_to), mDevice.getName()));
        }
    }
}

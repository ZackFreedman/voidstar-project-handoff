package com.example.zackfreedman.throwaway;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.wearable.view.WearableListView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TagHandlerService extends Service {
    private static final String TAG = "BuriedAlive TagHandlerService";
    private static final String IMMEDIATE_ALERT_SERVICE = "00001802-0000-1000-8000-00805F9B34FB";
    private static final String LINK_LOSS_SERVICE = "00001803-0000-1000-8000-00805F9B34FB";

    private Handler handler = new Handler();
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private boolean isScanningBle = false;

    private List<BluetoothDevice> tags = new ArrayList<BluetoothDevice>(); // TODO: Populate this
    private Map<String, Boolean> tagCheckednesses = new HashMap<String, Boolean>();

    private TagHandlerBinder binder = new TagHandlerBinder();
    private List<ScanEndCallback> scanEndCallbacks = new ArrayList<ScanEndCallback>();
    private CommandWriteCallback activeCommandCallback;

    public TagHandlerService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "beep");
        return binder;
    }

    public static abstract class ScanEndCallback {
        public abstract void onScanEnd();
    }

    public static abstract class CommandWriteCallback {
        public abstract void onResult(boolean success);
    }

    public class TagHandlerBinder extends Binder {
        public void initializeBle() {
            TagHandlerService.this.initializeBle();
        }

        public void startScanningBle(long period) {
            TagHandlerService.this.startScanningBle(period);
        }

        public void stopScanningBle() {
            TagHandlerService.this.stopScanningBle();
        }

        public void assignScanEndCallback(ScanEndCallback callback) {
            scanEndCallbacks.add(callback);
        }

        public List<BluetoothDevice> getTags() {
            return tags;
        }

        public Map<String, Boolean> getTagCheckednesses() {
            return tagCheckednesses;
        }

        // Only one of these can be active at once!
        public void beepTag(BluetoothDevice tag, CommandWriteCallback callback) {
            activeCommandCallback = callback;
            TagHandlerService.this.beepTag(tag);
        }

        public void stopBeepTag(BluetoothDevice tag, CommandWriteCallback callback) {
            activeCommandCallback = callback;
            TagHandlerService.this.stopBeepTag(tag);
        }

        public void setLinkLossAlert(BluetoothDevice tag, CommandWriteCallback callback) {
            activeCommandCallback = callback;
            TagHandlerService.this.setLinkLossAlert(tag);
        }

        public void clearLinkLossAlert(BluetoothDevice tag, CommandWriteCallback callback) {
            activeCommandCallback = callback;
            TagHandlerService.this.clearLinkLossAlert(tag);
        }
    }

    protected void initializeBle() {
        bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        Log.d(TAG, "BLE initialized");
    }

    protected void startScanningBle(long period) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() { // Set a delayed stop in [timeout] ms
                isScanningBle = false; // Timed out
                bluetoothAdapter.stopLeScan(leScanCallback);
                Log.d(TAG, "BLE scan time is up");
                for (ScanEndCallback callback : scanEndCallbacks) {
                    callback.onScanEnd();
                }
            }
        }, period);

        UUID[] targets = {
                UUID.fromString(LINK_LOSS_SERVICE),
                UUID.fromString(IMMEDIATE_ALERT_SERVICE)
        };

        isScanningBle = true;
        bluetoothAdapter.startLeScan(targets, leScanCallback);
        //bluetoothAdapter.startLeScan(leScanCallback);
        Log.d(TAG, "BLE scanning started");
    }

    protected void stopScanningBle() {
        isScanningBle = false;
        bluetoothAdapter.stopLeScan(leScanCallback);
        Log.d(TAG, "BLE scanning stopped");
    }

    protected BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            if (!tags.contains(device)) {
                Log.d(TAG, "Found device " + device.getName() + " w/ address " + device.getAddress());
                tags.add(device);
            }
        }
    };

    protected BluetoothGattCallback linkLossCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to link loss target");
                gatt.discoverServices();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "Writing link loss immediate alert 2");
            BluetoothGattService service = gatt.getService(UUID.fromString(LINK_LOSS_SERVICE));
            if (service != null) {
                BluetoothGattCharacteristic characteristic =
                        service.getCharacteristic(UUID.fromString("00002A06-0000-1000-8000-00805F9B34FB")); // Alert level?
                characteristic.setValue(new byte[]{2});
                gatt.writeCharacteristic(characteristic);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (characteristic.getUuid().equals(UUID.fromString("00002A06-0000-1000-8000-00805F9B34FB")) &&
                    characteristic.getValue()[0] == 2) {
                if (status == BluetoothGatt.GATT_SUCCESS) activeCommandCallback.onResult(true);
                else activeCommandCallback.onResult(false);
            }
            else activeCommandCallback.onResult(false);
        }
    };

    protected BluetoothGattCallback linkLossClearCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to link loss clear target");
                gatt.discoverServices();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "Writing link loss immediate alert 0");
            BluetoothGattService service = gatt.getService(UUID.fromString(LINK_LOSS_SERVICE));
            if (service != null) {
                BluetoothGattCharacteristic characteristic =
                        service.getCharacteristic(UUID.fromString("00002A06-0000-1000-8000-00805F9B34FB")); // Alert level?
                characteristic.setValue(new byte[]{0});
                gatt.writeCharacteristic(characteristic);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (characteristic.getUuid().equals(UUID.fromString("00002A06-0000-1000-8000-00805F9B34FB")) &&
                    characteristic.getValue()[0] == 0) {
                if (status == BluetoothGatt.GATT_SUCCESS) activeCommandCallback.onResult(true);
                else activeCommandCallback.onResult(false);
            }
            else activeCommandCallback.onResult(false);
        }
    };

    protected BluetoothGattCallback beepCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to beep target");
                gatt.discoverServices();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "Writing immediate alert 2");
            BluetoothGattService service = gatt.getService(UUID.fromString(IMMEDIATE_ALERT_SERVICE));
            if (service != null) {
                BluetoothGattCharacteristic characteristic =
                        service.getCharacteristic(UUID.fromString("00002A06-0000-1000-8000-00805F9B34FB")); // Alert level?
                characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                //characteristic.setValue(new byte[]{2});
                characteristic.setValue(new byte[]{0}); // TODO: Change this back before demo
                gatt.writeCharacteristic(characteristic);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (characteristic.getUuid().equals(UUID.fromString("00002A06-0000-1000-8000-00805F9B34FB")) &&
                    //characteristic.getValue()[0] == 2) {
                    characteristic.getValue()[0] == 0) { // TODO: Change this back before demo
                Log.d(TAG, "The tag would normally be beeping like crazy right now");
                if (status == BluetoothGatt.GATT_SUCCESS) activeCommandCallback.onResult(true);
                else activeCommandCallback.onResult(false);
            }
            else activeCommandCallback.onResult(false);
        }
//        }
    };

    protected BluetoothGattCallback beepStopCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to beep stop target");
                gatt.discoverServices();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "Writing immediate alert 0");
            BluetoothGattService service = gatt.getService(UUID.fromString(IMMEDIATE_ALERT_SERVICE));
            if (service != null) {
                BluetoothGattCharacteristic characteristic =
                        service.getCharacteristic(UUID.fromString("00002A06-0000-1000-8000-00805F9B34FB")); // Alert level?
                characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                characteristic.setValue(new byte[]{0});
                gatt.writeCharacteristic(characteristic);
            }
        }
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (characteristic.getUuid().equals(UUID.fromString("00002A06-0000-1000-8000-00805F9B34FB")) &&
                    characteristic.getValue()[0] == 0) {
                if (status == BluetoothGatt.GATT_SUCCESS) activeCommandCallback.onResult(true);
                else activeCommandCallback.onResult(false);
            }
            else activeCommandCallback.onResult(false);
        }
//        }
    };

    private void beepTag(BluetoothDevice tag) {
        Log.d(TAG, "Let's beep");
        tag.connectGatt(this, false, beepCallback);
    }

    private void stopBeepTag(BluetoothDevice tag) {
        Log.d(TAG, "Let's not beep");
        tag.connectGatt(this, false, beepStopCallback);
    }

    private void setLinkLossAlert(BluetoothDevice tag) {
        Log.d(TAG, "Link loss alert engaged");
        tag.connectGatt(this, false, linkLossCallback);
    }

    private void clearLinkLossAlert(BluetoothDevice tag) {
        Log.d(TAG, "Link loss alert cleared");
        tag.connectGatt(this, false, linkLossClearCallback);
    }
}

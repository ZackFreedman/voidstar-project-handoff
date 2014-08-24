package com.example.zackfreedman.throwaway;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.wearable.view.WearableListView;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingDeque;

public class MainActivity extends Activity {
    private static final String TAG = "BuriedAlive";

    private boolean tagListLaunched;
    private boolean serviceBound = false;
    private TagHandlerService.TagHandlerBinder tagHandlerBinder;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (service instanceof TagHandlerService.TagHandlerBinder) {
                serviceBound = true;
                tagHandlerBinder = (TagHandlerService.TagHandlerBinder) service;
                tagHandlerBinder.initializeBle();
                tagHandlerBinder.startScanningBle(10000);
                tagHandlerBinder.assignScanEndCallback(new TagHandlerService.ScanEndCallback(){
                    @Override
                    public void onScanEnd() {
                        tagListLaunched = true;
                        Intent intent = new Intent(MainActivity.this, TagListActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    }
                });
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startService(new Intent(this, TagHandlerService.class));
        bindService(new Intent(this, TagHandlerService.class), connection, 0);
        setContentView(R.layout.throbberlayout);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Log.d(TAG, "Search your library for up to three creature cards and put them into your graveyard. Then shuffle your library.");
    }

    @Override
    protected void onPause() {
        if (serviceBound) {
            unbindService(connection);
            serviceBound = false;
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (!tagListLaunched) {
            stopService(new Intent(this, TagHandlerService.class));
        }
        super.onStop();
    }
}


package com.example.zackfreedman.throwaway;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.wearable.view.WearableListView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.zackfreedman.throwaway.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TagListActivity extends Activity implements WearableListView.ClickListener {
    private static final String TAG = "BuriedAlive TagListActivity";

    private List<BluetoothDevice> tags = new ArrayList<BluetoothDevice>();
    private Map<String, Boolean> tagCheckednesses = new HashMap<String, Boolean>();

    private boolean serviceBound = false;
    private TagHandlerService.TagHandlerBinder tagHandlerBinder;

    private WearableListView listView;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (service instanceof TagHandlerService.TagHandlerBinder) {
                serviceBound = true;
                tagHandlerBinder = (TagHandlerService.TagHandlerBinder) service;
                tags = tagHandlerBinder.getTags();
                tagCheckednesses = tagHandlerBinder.getTagCheckednesses();
                if (listView != null) {
                    if (tags.size() > 0) {
                        listView.getAdapter().notifyDataSetChanged();
                        findViewById(R.id.textView).setVisibility(View.INVISIBLE);
                    }
                    else {
                        listView.setVisibility(View.INVISIBLE);
                    }
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false; // TODO: Ensure it's the TagHandlerService
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindService(new Intent(this, TagHandlerService.class), connection, 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        listView = (WearableListView) findViewById(R.id.wearable_list_view);
        listView.setAdapter(new TagAdapter());
        listView.setClickListener(this);
        listView.setInitialOffset(-90);
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
        stopService(new Intent(this, TagHandlerService.class));
        super.onStop();
    }

    public class TagAdapter extends WearableListView.Adapter {
        @Override
        public WearableListView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            return new WearableListView.ViewHolder(
                    getLayoutInflater().inflate(R.layout.scan_result_item, null));
        }

        @Override
        public void onBindViewHolder(WearableListView.ViewHolder viewHolder, int i) {
            View view = viewHolder.itemView;
            TextView textView = (TextView) view.findViewById(R.id.tagName);
            ImageView imageView = (ImageView) view.findViewById(R.id.checkmark);

            String mac = tags.get(i).getAddress();
            String crunchedMac = mac.replace(":", "").substring(8, 12);
            Log.d(TAG, "mac: " + mac + " crunchedMac: " + crunchedMac);

            view.setTag(i);

            textView.setText("Tag " + crunchedMac);

            if (tagCheckednesses.containsKey(mac) &&
                    tagCheckednesses.get(mac)) {
                //imageView.setVisibility(View.VISIBLE);
                imageView.setImageResource(R.drawable.check);
            }
            else {
                //imageView.setVisibility(View.INVISIBLE);
                imageView.setImageResource(R.drawable.check_grayscale);
            }
        }

        @Override
        public int getItemCount() {
            return tags.size();
        }
    }

    @Override
    public void onClick(final WearableListView.ViewHolder viewHolder) {
        final BluetoothDevice tag = tags.get((Integer) viewHolder.itemView.getTag());
        final ImageView checkmark = (ImageView) viewHolder.itemView.findViewById(R.id.checkmark);
        final View throbber = viewHolder.itemView.findViewById(R.id.connecting_throbber);
        final String mac = tag.getAddress();

        Log.d(TAG, "Tapped " + mac);

        setItemState(viewHolder, STATE_INDETERMINATE);

        if (tagCheckednesses.containsKey(mac)) {
            tagCheckednesses.put(mac,
                    !tagCheckednesses.get(mac));
        }
        else {
            tagCheckednesses.put(mac, true);
        }

        if (tagCheckednesses.get(mac) == true) {
            //viewHolder.itemView.findViewById(R.id.checkmark).setVisibility(View.VISIBLE);
            tagHandlerBinder.beepTag(tag, new TagHandlerService.CommandWriteCallback() {
                @Override
                public void onResult(boolean success) { // Beep start
                    if (success) {
                        Log.d(TAG, "Beep started. Setting link loss alert.");
                        tagHandlerBinder.setLinkLossAlert(tag, new TagHandlerService.CommandWriteCallback() {
                            @Override
                            public void onResult(boolean success) { // Link loss set
                                if (success) {
                                    Log.d(TAG, "Link loss alert set. Stopping beep.");
                                    tagHandlerBinder.stopBeepTag(tag, new TagHandlerService.CommandWriteCallback() {
                                        @Override
                                        public void onResult(boolean success) { // Beep end
                                            setItemState(viewHolder, STATE_CHECKED);
                                        }
                                    });
                                }
                                else {
                                    tagCheckednesses.put(mac, false);
                                    setItemState(viewHolder, STATE_UNCHECKED);
                                    failFromUnchecked();
                                }
                                // TODO
                            }
                        });
                    }
                    else {
                        tagCheckednesses.put(mac, false);
                        setItemState(viewHolder, STATE_UNCHECKED);
                        failFromUnchecked();
                    }
                    // TODO
                }
            });
        }
        else {
            //viewHolder.itemView.findViewById(R.id.checkmark).setVisibility(View.INVISIBLE);
            tagHandlerBinder.clearLinkLossAlert(tag, new TagHandlerService.CommandWriteCallback() {
                @Override
                public void onResult(boolean success) {
                    if (success) {
                        setItemState(viewHolder, STATE_UNCHECKED);
                    } else {
                        tagCheckednesses.put(mac, true);
                        setItemState(viewHolder, STATE_CHECKED);
                        failFromChecked();
                    }
                    // TODO
                }
            });
        }
    }

    @Override
    public void onTopEmptyRegionClick() {

    }

    private void failFromChecked() {
        Log.d(TAG, "Failed while checked");
    }

    private void failFromUnchecked() {
        Log.d(TAG, "Failed while unchecked");
    }

    private final static int STATE_UNCHECKED = 0;
    private final static int STATE_INDETERMINATE = 1;
    private final static int STATE_CHECKED = 2;
    private void setItemState(final WearableListView.ViewHolder viewHolder, final int newState) {
        runOnUiThread(new Runnable(){
            @Override
            public void run() {
                final ImageView checkmark = (ImageView) viewHolder.itemView.findViewById(R.id.checkmark);
                final View throbber = viewHolder.itemView.findViewById(R.id.connecting_throbber);

                if (newState == STATE_INDETERMINATE) {
                    checkmark.setVisibility(View.INVISIBLE);
                    throbber.setVisibility(View.VISIBLE);
                }
                else if (newState == STATE_CHECKED) {
                    checkmark.setVisibility(View.VISIBLE);
                    checkmark.setImageResource(R.drawable.check);
                    throbber.setVisibility(View.INVISIBLE);
                }
                else if (newState == STATE_UNCHECKED) {
                    checkmark.setVisibility(View.VISIBLE);
                    checkmark.setImageResource(R.drawable.check_grayscale);
                    throbber.setVisibility(View.INVISIBLE);
                }
            }
        });
    }
}

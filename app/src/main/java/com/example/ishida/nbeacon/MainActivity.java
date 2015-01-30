package com.example.ishida.nbeacon;

import android.app.Activity;
import android.app.ActionBar;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.os.Build;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.util.UUID;


public class MainActivity extends Activity {

    static final int APPLE = 0x004c;
    static final UUID uuid = UUID.fromString("e2c56db5-dffb-48d2-b060-d0f5a71096e0");
    static final String TAG = "nBeacon";

    private PlaceholderFragment frag;
    private BluetoothAdapter bTAdapter;
    private BluetoothLeAdvertiser bTAdvertiser;
    private AdvertiseCallback advCallback;
    private boolean isAdvertised = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        frag = new PlaceholderFragment();
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, frag)
                    .commit();
        }
        init();
    }

    @Override
    protected void onStart() {
        super.onStart();
        startAdvertise();
    }

    @Override
    protected void onStop() {
        super.onStop();

        stopAdvertise();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void init() {
        // BLE check
        if (!isBLESupported()) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            Log.d(TAG, getString(R.string.ble_not_supported));
            finish();
            return;
        }

        // BT check
        BluetoothManager manager = getBTManager();
        if (manager != null) {
            bTAdapter = manager.getAdapter();
        }
        if (bTAdapter == null) {
            Toast.makeText(this, R.string.bt_unavailable, Toast.LENGTH_SHORT).show();
            Log.d(TAG, getString(R.string.bt_unavailable));
            finish();
            return;
        }

        // BT LE adv check
        if (!bTAdapter.isMultipleAdvertisementSupported()) {
            Toast.makeText(this, R.string.ble_adv_not_supported, Toast.LENGTH_SHORT).show();
            Log.d(TAG, getString(R.string.ble_adv_not_supported));

            finish();
            return;
        }

        advCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                if (settingsInEffect != null) {
                    appendStatus(settingsInEffect.toString());
                } else {
                    appendStatus("onStartSuccess: settingInEffect = null");
                }
            }

            @Override
            public void onStartFailure(int errorCode) {
                appendStatus("onStartFailure: errorCode = " + errorCode);
            }
        };

        Log.d(TAG,getString(R.string.ble_initialized));
    }

    private void startAdvertise() {
        if(bTAdapter != null && !isAdvertised) {
            if (bTAdvertiser == null) {
                bTAdvertiser = bTAdapter.getBluetoothLeAdvertiser();
            }
            bTAdvertiser.startAdvertising(createAdvSettings(), createAdvData(), advCallback);
            appendStatus(getString(R.string.ble_start_adv));
        }
    }

    private void stopAdvertise() {
        if(bTAdvertiser != null) {
            bTAdvertiser.stopAdvertising(advCallback);
            isAdvertised = false;
            appendStatus(getString(R.string.ble_stop_adv));
        }
    }

    /** check if BLE Supported device */
    public boolean isBLESupported() {
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    /** get BluetoothManager */
    public BluetoothManager getBTManager() {
        return (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
    }

    private static AdvertiseData createAdvData() {
        // 某Beacon
        /*
        final byte[] manufacturerData = new byte[] {
                (byte) 0x02, (byte) 0x15, // fix
                // proximity uuid 01020304-0506-0708-1112-131415161718
                (byte) 0xe2, (byte) 0xc5, (byte) 0x6d, (byte) 0xb5, // uuid
                (byte) 0xdf, (byte) 0xfb, (byte) 0x48, (byte) 0xd2, // uuid
                (byte) 0xb0, (byte) 0x60, (byte) 0xd0, (byte) 0xf5, // uuid
                (byte) 0xa7, (byte) 0x10, (byte) 0x96, (byte) 0xe0, // uuid
                (byte) 0x00, (byte) 0x01, // major 257
                (byte) 0x00, (byte) 0x01, // minor 514
                (byte) 0xc5 // Tx Power -59
        };
        */
        final byte[] manufacturerData = createManufactureData();
        AdvertiseData.Builder builder = new AdvertiseData.Builder()
               .setIncludeTxPowerLevel(false)
               .addManufacturerData(APPLE, manufacturerData);
        return builder.build();
    }

    private static AdvertiseSettings createAdvSettings() {
        AdvertiseSettings.Builder builder = new AdvertiseSettings.Builder()
             .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
             .setConnectable(true)
             .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER);
        return builder.build();
    }

    private static byte[] createManufactureData() {
        ByteBuffer bb = ByteBuffer.allocate(23);

            bb.putShort((short) 0x0215);
            bb.putLong(uuid.getMostSignificantBits());
            bb.putLong(uuid.getLeastSignificantBits());
            bb.putShort((short) 0x0001);
            bb.putShort((short) 0x0001);
            bb.put((byte) 0xc5);

        return bb.array();
    }

    public void appendStatus(String status) {
        frag.appendStatus(status);
    }
    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {

        TextView textview;
        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            textview = (TextView)rootView.findViewById(R.id.text);
            return rootView;
        }

        public void appendStatus(String status) {
            String current = textview.getText().toString();
            textview.setText(current + "\n" + status);
        }
    }
}

package com.example.ishida.nbeacon;

import android.app.Activity;
import android.app.ActionBar;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.os.Build;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class MainActivity extends Activity {

    static final int APPLE = 0x004c;
    static final UUID uuid = UUID.fromString("e2c56db5-dffb-48d2-b060-d0f5a71096e0");
    static final String TAG = "nBeacon";

    private PlaceholderFragment frag;
    private BluetoothAdapter bTAdapter;
    private BluetoothLeAdvertiser bTAdvertiser;
    private AdvertiseCallback advCallback;
    private BluetoothGattServer gattServer;
    private BluetoothGattServerCallback gattCallback;
    private boolean isAdvertised = false;

    static final UUID service_uuid = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb");
    static final UUID characteristic_uuid = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb");
    static final UUID characteristic_uuid2 = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb");

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

        frag.editText.setText(R.string.initial_text);

        frag.editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                Log.d(TAG, "aId=" + actionId + ", " + event);
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    notifyCharacteristicChanged();
                }
                return false;
            }
        });
        startAdvertise();
        startGattServer();
    }

    @Override
    protected void onStop() {
        super.onStop();

        stopGattServer();
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

        gattCallback = new BluetoothGattServerCallback() {
            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                Log.d(TAG, "onConnectionStateChange: status=" + status + "->" + newState);
            }

            @Override
            public void onServiceAdded(int status, BluetoothGattService service) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    appendStatus("onServiceAdded: status=GATT_SUCCESS service="
                            + service.getUuid().toString());
                    Log.d(TAG, "onServiceAdded: status=GATT_SUCCESS service="
                                    + service.getUuid().toString());
                } else {
                    appendStatus("onServiceAdded: status!=GATT_SUCCESS");
                    Log.d(TAG, "onServiceAdded: status!=GATT_SUCCESS");
                }

            }

            @Override
            public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
                Log.d(TAG, "onCharacteristicReadRequest: requestId=" + requestId + " offset=" + offset);
                Log.d(TAG, "uuid: " + characteristic.getUuid().toString());
                if (characteristic.getUuid().equals(characteristic_uuid)) {
                    Log.d(TAG, "reading characteristic");
                    characteristic.setValue(frag.editText.getText().toString());
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());
                }
            }

            @Override
            public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                Log.d(TAG, "onCharacteristicWriteRequest: requestId=" + requestId + " preparedWrite="
                                + Boolean.toString(preparedWrite) + " responseNeeded="
                                + Boolean.toString(responseNeeded) + " offset=" + offset);
            }

            @Override
            public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
                Log.d(TAG, "onDescriptorReadRequest: ");
            }

            @Override
            public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                Log.d(TAG, "onDescriptorWriteRequest: ");
            }

            @Override
            public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
                Log.d(TAG, "onExecuteWrite: ");
            }

            @Override
            public void onNotificationSent(BluetoothDevice device, int status) {
                Log.d(TAG, "onNotificationSent: " + device);
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
            bTAdvertiser = null;
            appendStatus(getString(R.string.ble_stop_adv));
        }
    }

    private void startGattServer() {
        gattServer = getBTManager().openGattServer(this, gattCallback);

        BluetoothGattService gs = new BluetoothGattService(
            service_uuid, BluetoothGattService.SERVICE_TYPE_PRIMARY
        );

        BluetoothGattCharacteristic gc = new BluetoothGattCharacteristic(
            characteristic_uuid, BluetoothGattCharacteristic.PROPERTY_READ|BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        );

        gs.addCharacteristic(gc);
        gattServer.addService(gs);
    }


    private void stopGattServer() {
        if (gattServer != null) {
            gattServer.clearServices();
            gattServer.close();
            gattServer = null;
            appendStatus(getString(R.string.stop_gatt_server));
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

    public void notifyCharacteristicChanged() {
        List<BluetoothDevice> devices = getBTManager().getConnectedDevices(BluetoothProfile.GATT_SERVER);
        if (devices.isEmpty()) return;
        BluetoothGattService service = gattServer.getService(service_uuid);
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristic_uuid);
        characteristic.setValue(frag.editText.getText().toString());

        List<BluetoothDevice> notifiedDevices = new ArrayList<BluetoothDevice>();

        boolean alreadyNotifed = false;
        for (BluetoothDevice device : devices) {
            for (BluetoothDevice notifiedDevice : notifiedDevices) {
                Log.d(TAG, notifiedDevice.getAddress() + "vs," + device.getAddress());
                if (notifiedDevice.getAddress().equals(device.getAddress())) {
                    Log.d(TAG, "already notified: " + device);
                    alreadyNotifed = true;
                    break;
                }
            }
            if (alreadyNotifed) continue;
            gattServer.notifyCharacteristicChanged(device, characteristic, false);
            notifiedDevices.add(device);
            alreadyNotifed = false;
        }
    }

    private static AdvertiseData createAdvData() {
        final byte[] manufacturerData = createManufactureData();
        AdvertiseData.Builder builder = new AdvertiseData.Builder()
               .setIncludeTxPowerLevel(false)
               .addManufacturerData(APPLE, manufacturerData);
               //.addServiceUuid(ParcelUuid.fromString("00001802-0000-1000-8000-00805f9b34fb"));
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
        EditText editText;

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            textview = (TextView)rootView.findViewById(R.id.text);
            editText = (EditText)rootView.findViewById(R.id.editText);
            return rootView;
        }

        public void appendStatus(String status) {
            String current = textview.getText().toString();
            textview.setText(current + "\n" + status);
        }


    }
}

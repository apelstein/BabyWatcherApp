package com.example.myapplication;

import android.Manifest;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.telephony.SmsManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.location.LocationManager;
import android.location.LocationListener;
import android.location.Geocoder;
import android.location.Address;
import java.util.Locale;
import java.io.*;

import android.content.pm.PackageManager;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.EditText;
import android.content.DialogInterface;
import android.text.InputType;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.os.Handler;
import android.app.Activity;

import java.util.List;
import java.util.UUID;

import android.content.Context;

public class MainActivity extends AppCompatActivity {
    private static final int MY_PERMISSIONS= 123;
//    private LeDeviceListAdapter mLeDeviceListAdapter;
    private Context mContext;
    private Activity mActivity;
    private BluetoothAdapter bluetoothAdapter;
    public String LatestAddress = "";
    public SharedPreferences.Editor editor;
    SharedPreferences pref;
    private AudioManager audioManager;
    private MediaPlayer mp;
    private boolean isBabySaved = false;

    private final static int REQUEST_ENABLE_BT = 1;
    private BluetoothAdapter mBluetoothAdapter;

    private TextView title;
    //////////////////////////////////////////////////////////////////////////////


    private BluetoothLeService mBluetoothLeService;
    private String mDeviceAddress;

    private boolean mConnected = false;

    private PresMeasurments measurments = new PresMeasurments();


    private static final String TAG = BabyLocator.class.getSimpleName();

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private BluetoothGattCharacteristic mModeCharacteristic;
    private BluetoothGattCharacteristic mMeasurementCharacteristic;
    private boolean btInitAllready = false;
    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;

                //updateConnectionState(R.string.connected);
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {

                btInitAllready = false;
                scanLeDevice(true);
                if(measurments.getAvgMeasuere()){
                    babyForgotten();
                    final Handler myHand = new Handler();
                    myHand.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if(!isBabySaved){
                                notifyContact();
                            }
                        }
                    }, 5000);
                }
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                //get current multimeter mode
                BluetoothGattService multimeterService =
                        mBluetoothLeService.getService(UUID.fromString(BabyGattAtrributes.BABY_SERVICE));
                mModeCharacteristic =
                        multimeterService.getCharacteristic(UUID.fromString(BabyGattAtrributes.BABY_MODE));
                mBluetoothLeService.readCharacteristic(mModeCharacteristic);
                mMeasurementCharacteristic =
                        multimeterService.getCharacteristic(UUID.fromString(BabyGattAtrributes.BABY_MEASUREMENT));
                // Show all the supported services and characteristics on the user interface.
                //displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_MODE_AVAILABLE.equals(action)) {
                int mode = intent.getIntExtra(BluetoothLeService.DATA,-1);

                //set mode
                if (mode == -1) {
                    // should not get here
                    Toast.makeText(getApplicationContext(), "Error occured - Invalid mode", Toast.LENGTH_SHORT).show();
                }
//                else if (mode == 0) {
//
//                    //      value.setBackgroundResource(R.drawable.measbackoff);
//                    //unsubscribe notifications
//                    mBluetoothLeService.setCharacteristicNotification(mMeasurementCharacteristic, false);
//
//
//                }
                else {
                    if(mBluetoothLeService != null && !btInitAllready) {
                        mBluetoothLeService.writeCharacteristic(mModeCharacteristic, 1);
                        btInitAllready = true;
                        mBluetoothLeService.setCharacteristicNotification(mMeasurementCharacteristic, true);
                    }

                }

            } else if (BluetoothLeService.ACTION_MEASUREMENT_AVAILABLE.equals(action)) {
                long measValueInMicro = intent.getLongExtra(BluetoothLeService.DATA,-1);
                if (measValueInMicro > 2140000000) {
                    title.setText("nottt");
                    measurments.insertMeasure(false);
//                    babySaved();
                }
                else{
                    title.setText("yesss");
                    measurments.insertMeasure(true);
//                    babyForgotten();

                }


            }
        }
    };


    @Override
    protected void onPause() {
        super.onPause();
//        if (false) {
//        unregisterReceiver(mGattUpdateReceiver);
//        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        if (false) {
        mBluetoothLeService.disconnect();

        unbindService(mServiceConnection);
        mBluetoothLeService = null;
//        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_MEASUREMENT_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_MODE_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_NOTIFICATION_ENABLED);
        return intentFilter;
    }


    public void exitOnClick(View v){
        // Disconnect and return to intro page
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this);
        builder.setMessage("Exit Multimeter?").setTitle("Please confirm exit");
        builder.setPositiveButton("Exit", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                mBluetoothLeService.disconnect();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User cancelled the dialog
            }
        });
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();
    }

    //////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mActivity = MainActivity.this;
        mContext = getApplicationContext();
        pref = getApplicationContext().getSharedPreferences("MyPref", MODE_PRIVATE);
        editor = pref.edit();
        editor.apply();
        title = (TextView) findViewById(R.id.hello_w);
        FloatingActionButton alrm = findViewById(R.id.alarm);
        alrm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isBabySaved = true;
                babySaved();
            }
        });
        FloatingActionButton gps = findViewById(R.id.gps);
        gps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getLoc(view);
            }
        });
        FloatingActionButton addNum = findViewById(R.id.number);
        addNum.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addNumber(view);
            }
        });


        /* verify (or request) permissions - GPS and SMS */
        checkPermission();
        getLoc(findViewById(R.id.gps));
        blefind(findViewById(R.id.another));
        }
//    }

    public void onResume(){
        super.onResume();


//        final Handler hand=new Handler();
//        hand.postDelayed(new Runnable() {
//
//            @Override
//            public void run() {
//
//                hand.postDelayed(this, 10000);
//                /* THIS SECTION WILL BE CALLED EVERY X (10000 for now) milli secs
//                *  if connected to board:
//                *       ask for pressure value and act accordingly
//                */
//
//            }
//        }, 10000);
    }

    public void babyForgotten(View view){
        Snackbar.make(view, "Trying to up the volume!", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
        AudioManager audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
      //  audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND);
        if (audioManager != null) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_PLAY_SOUND);
            final MediaPlayer mp = MediaPlayer.create(this, R.raw.lux);
            mp.start();

        }
    }

    public void babyForgotten(){
        if (audioManager != null) {
            return;
        }
//        Snackbar.make(view, "Trying to up the volume!", Snackbar.LENGTH_LONG)
//                .setAction("Action", null).show();
        audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        //  audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND);
        if (audioManager != null) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_PLAY_SOUND);
//            final MediaPlayer mp = MediaPlayer.create(this, R.raw.lux);
            mp = MediaPlayer.create(this, R.raw.lux);
            mp.start();

        }
    }

    public void babySaved(){

//        Snackbar.make(view, "Trying to up the volume!", Snackbar.LENGTH_LONG)
//                .setAction("Action", null).show();
        //  audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND);
        if (audioManager != null) {
//            final MediaPlayer mp = MediaPlayer.create(this, R.raw.lux);
            if(mp != null){
                mp.stop();
            }

        }
        mp = null;
        audioManager = null;
    }

    public void blefind(View view){

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }else{
            Toast.makeText(this, R.string.ble_supported, Toast.LENGTH_SHORT).show();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();

            // Checks if Bluetooth is supported on the device AND turned on, o/w asks the user to turn on.
            // TODO: Try to turn BT on independently, w/o user involvement
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
            while (!startBleScan()){
                continue;
            }
        }
    }

    private boolean startBleScan() {
        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        if (!bluetoothAdapter.isEnabled()){
            return false;
        }
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        scanLeDevice(true);
        return true;
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            mBluetoothAdapter.getBluetoothLeScanner().startScan(mLeScanCallback);
        } else {
            mBluetoothAdapter.getBluetoothLeScanner().stopScan(mLeScanCallback);
        }
    }

    // Device scan callback.
    private ScanCallback mLeScanCallback =
            new ScanCallback() {
                //                @Override
//                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
//                    runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            mLeDeviceListAdapter.addDevice(device);
//                            mLeDeviceListAdapter.notifyDataSetChanged();
//                        }
//                    });
//                }
                @Override
                public void onScanResult(int callbackType, final ScanResult result) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            if (result.getDevice().getName()!=null && result.getDevice().getName().equals("BabyWatcher")) {
                                scanLeDevice(false);
                                Toast.makeText(MainActivity.this, R.string.BabyWatcher_FOUND, Toast.LENGTH_SHORT).show();
                                mDeviceAddress = result.getDevice().getAddress();
                                startBleService();
//                                final Intent intent = new Intent(MainActivity.this, BabyLocator.class);
//                                intent.putExtra(BabyLocator.EXTRAS_DEVICE_NAME, result.getDevice().getName());
//                                intent.putExtra(BabyLocator.EXTRAS_DEVICE_ADDRESS, result.getDevice().getAddress());
//                                startActivity(intent);
                            }
                        }
                    });

                }
            };




    private void startBleService() {
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        boolean b = bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        if (b) {
            System.out.println("sdfsdf");
        }
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    LocationListener locationListenerGPS=new LocationListener() {
        @Override
        public void onLocationChanged(android.location.Location location) {
            double latitude=location.getLatitude();
            double longitude=location.getLongitude();
            //msgLoc="New Latitude: "+latitude + "New Longitude: "+longitude;
            try {
                Geocoder geocoder;
                List<Address> addresses;
                geocoder = new Geocoder(mContext, Locale.getDefault());

                addresses = geocoder.getFromLocation(latitude, longitude, 1);
                LatestAddress = addresses.get(0).getAddressLine(0);
              //  System.out.println(address);
            }catch (IOException e){
                e.printStackTrace();
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    };

    public void getLoc(View view) {
        if (view.getContext().checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                view.getContext().checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationManager locationManager = (LocationManager)
                    getSystemService(Context.LOCATION_SERVICE);
            if (locationManager != null) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListenerGPS);
                Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location != null) {
                    Snackbar.make(view, "Trying to get device location! " + LatestAddress, Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                }
            }
        }
    }
    public void addNumber(View view){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add contact's number");

    // Set up the input
        final EditText input = new EditText(this);
    // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_CLASS_PHONE);
        int def = pref.getInt("num",0);
        String fullNum = "0" + def;
        input.setText(fullNum);
        builder.setView(input);

    // Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String m_Text;
                m_Text = input.getText().toString();
                editor.remove("num");
                editor.putInt("num", Integer.parseInt(m_Text));
                editor.apply();
           //     notifyContact();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                System.out.println("$$$$$$$$$$$$$$: " + "0" + pref.getInt("num", 0));
            }
        });

        builder.show();
    }

    /* A function that sends SMS (with car's location?) */
    public void notifyContact(){
//Get the SmsManager instance and call the sendTextMessage method to send message
        SmsManager sms=SmsManager.getDefault();
        int number = pref.getInt("num",0);
        String phoneNum = "0" + number;
        sms.sendTextMessage(phoneNum, null, "Go help the baby!\n location:   " + LatestAddress , null,null);
    }



    protected void checkPermission(){
        if(ContextCompat.checkSelfPermission(mActivity,Manifest.permission.ACCESS_COARSE_LOCATION)
                + ContextCompat.checkSelfPermission(
                mActivity,Manifest.permission.ACCESS_FINE_LOCATION)
                + ContextCompat.checkSelfPermission(
                mActivity,Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {

            // Do something, when permissions not granted
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    mActivity, Manifest.permission.ACCESS_COARSE_LOCATION)
                    || ActivityCompat.shouldShowRequestPermissionRationale(
                    mActivity, Manifest.permission.ACCESS_FINE_LOCATION)
                    || ActivityCompat.shouldShowRequestPermissionRationale(
                    mActivity, Manifest.permission.SEND_SMS)) {
                // If we should give explanation of requested permissions

                // Show an alert dialog here with request explanation
                AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
                builder.setMessage("Camera, Read Contacts and Write External" +
                        " Storage permissions are required to do the task.");
                builder.setTitle("Please grant those permissions");
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ActivityCompat.requestPermissions(
                                mActivity,
                                new String[]{
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.SEND_SMS
                                },
                                MY_PERMISSIONS
                        );
                    }
                });
                builder.setNeutralButton("Cancel", null);
                AlertDialog dialog = builder.create();
                dialog.show();
            } else {
                // Directly request for required permissions, without explanation
                ActivityCompat.requestPermissions(
                        mActivity,
                        new String[]{
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.SEND_SMS
                        },
                        MY_PERMISSIONS
                );
            }
        }
    }
}

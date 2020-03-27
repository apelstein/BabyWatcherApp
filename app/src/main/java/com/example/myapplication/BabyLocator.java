package com.example.myapplication;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.IBinder;
//import android.support.v7.app.AlertDialog;
//import android.support.v7.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.UUID;

//import it.beppi.knoblibrary.Knob;

public class BabyLocator extends AppCompatActivity {

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    // knob states
    public static final int R_STATE = 0;
    public static final int MA500_STATE = 1;
    public static final int OFF_STATE = 2;
    public static final int V3_STATE = 3;
    public static final int V10_STATE = 4;

    // dimm colors
    public static final int FULL_DIM = 0xFF000000;
    public static final int THREEQUARTER_DIM = 0xBF000000;
    public static final int HALF_DIM = 0x80000000;
    public static final int QUARTER_DIM = 0x40000000;
    public static final int EMPTY_DIM = 0x00000000;


  //  private Knob knob;
    private TextView value;
    private TextView units;
    private TextView hold;
    private int dimState;
    private boolean isHold;

    private BluetoothLeService mBluetoothLeService;
    private String mDeviceName;
    private String mDeviceAddress;
    private boolean mConnected = false;
    private boolean mIsOff = true;

    private static final String TAG = BabyLocator.class.getSimpleName();
    private static final int OVERFLOW = 0xffffffff;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            System.out.println("!!!!!!!!!!!!!!! in onServiceConnected");
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
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
            System.out.println("~~~~~~~~~~~~~~~~ onReceive");
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
             //   knob.setEnabled(true);
                //updateConnectionState(R.string.connected);
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                // open intro activity and close current activity
                final Intent newIntent = new Intent(BabyLocator.this, MainActivity.class);
                startActivity(newIntent);
                finish();
                //updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                //clearUI();
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
                int knobnewState = -1;
                //set mode
                if (mode == -1) {
                    // should not get here
                    Toast.makeText(getApplicationContext(), "Error occured - Invalid mode", Toast.LENGTH_SHORT).show();
                }
                else if (mode == 0) {
                    mIsOff = true;
                    knobnewState = OFF_STATE;
                    units.setText("");
              //      value.setBackgroundResource(R.drawable.measbackoff);
                    hold.setTextColor(Color.WHITE);
                    //unsubscribe notifications
                    mBluetoothLeService.setCharacteristicNotification(mMeasurementCharacteristic, false);
                    // reset hold
                    isHold = false;
                    hold.setTextColor(Color.WHITE);
                    // reset dim
                    dimState = FULL_DIM;
                    value.setTextColor(dimState);
                }
                else {
                    if(mBluetoothLeService != null && !btInitAllready) {
                        mBluetoothLeService.writeCharacteristic(mModeCharacteristic,  1);
                        btInitAllready = true;
                    }
             //       value.setBackgroundResource(R.drawable.measback);
                    if (mode == 4) {
                        knobnewState = R_STATE;
                        if (!isHold) {
                            units.setText("Ω");
                        }
                    }
                    else if (mode == 3) {
                        knobnewState = MA500_STATE;
                        if (!isHold) {
                            units.setText("mA");
                        }
                    }
                    else {
                        if (!isHold) {
                            units.setText("V");
                        }
                        if (mode == 1) {
                            knobnewState = V3_STATE;
                        }
                        else {
                            knobnewState = V10_STATE;
                        }
                    }
                    if(mIsOff) {
                        mIsOff = false;
                        //subscribe notifications
                        mBluetoothLeService.setCharacteristicNotification(mMeasurementCharacteristic, true);
                    }
                    else {
                //        knob.setEnabled(true);
                    }
                }
               // if (knobnewState != knob.getState()) {
                //    knob.setState(knobnewState);
                //}
            } else if (BluetoothLeService.ACTION_MEASUREMENT_AVAILABLE.equals(action)) {
                long measValueInMicro = intent.getLongExtra(BluetoothLeService.DATA,-1);
                if (measValueInMicro > 2140000000) {
                    value.setText("nottt");
                }
                else{
                    value.setText("yesss");
                }
                //check if overflow
//                if((int) measValueInMicro == OVERFLOW) {
//                    value.setText(R.string.overflow);
//                }
//                else {
                    //convert micro to the desired prefix
//                    double measValue;
//                    if(knob.getState() == MA500_STATE) {
//                        //milli
//                        measValue = (double) measValueInMicro / 1000;
//                    }
//                    else {
//                        //none
//                        measValue = (double) measValueInMicro / 1000000;
//                    }
//                    value.setText("measvalue");//+ measValue);
//                }
            } else if (BluetoothLeService.ACTION_NOTIFICATION_ENABLED.equals(action)) {
                if(mIsOff) {
                    value.setText("");
                }
                else {
                    // reset only if this is not hold
                    if (!isHold) {
                        value.setText("0.00");
                    }
                }
      //          knob.setEnabled(true);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        System.out.println("~~~~~~~~~~~~~ onCreate BabyLocator!");
        setContentView(R.layout.activity_babylocator);

        // init knob
//        knob = (Knob) findViewById(R.id.knob);
//        knob.setOnStateChanged(knobChanged);
//        knob.setEnabled(false);
        value = (TextView) findViewById(R.id.txtValue);
        units = (TextView) findViewById(R.id.txtUnits);
        hold = (TextView) findViewById(R.id.txthold);

        // at the start hold is not active
        isHold = false;

        // set value font
//        Typeface myTypeface = Typeface.createFromAsset(getAssets(), "fonts/digital.ttf");
//        value.setTypeface(myTypeface);

        // set value color to highest dimm
        dimState = FULL_DIM;
        value.setTextColor(dimState);
        units.setTextColor(dimState);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
//        if (false) {
            Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        boolean b = bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        if(b){
            System.out.println("sdff");
        }
//        }
    }

    @Override
    protected void onResume() {
        System.out.println("~~~~~~~~~~~ onResume BabyLocator!");
        super.onResume();
        int pressure = getPressure();
        System.out.println("XxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXx Pressure: " + pressure);
//        if (false) {
            registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
            if (mBluetoothLeService != null) {
                final boolean result = mBluetoothLeService.connect(mDeviceAddress);
                Log.d(TAG, "Connect request result=" + result);
            } else {
                System.out.println("!!!!!!! in null :(");
            }
//        }
    }

    @Override
    protected void onPause() {
        super.onPause();
//        if (false) {
            unregisterReceiver(mGattUpdateReceiver);
//        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        if (false) {
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

//    Knob.OnStateChanged knobChanged = new Knob.OnStateChanged() {
//        @Override
//        public void onState(int state) {
//            knob.setEnabled(false);
//            switch (state) {
//                case R_STATE:
//                    Toast.makeText(getApplicationContext(), "Option not supported yet", Toast.LENGTH_SHORT).show();
//                    mBluetoothLeService.writeCharacteristic(mModeCharacteristic,  4);
//                    break;
//                case MA500_STATE:
//                    mBluetoothLeService.writeCharacteristic(mModeCharacteristic,  3);
//                    break;
//                case OFF_STATE:
//                    mBluetoothLeService.writeCharacteristic(mModeCharacteristic,  0);
//                    // deal
//                    break;
//                case V3_STATE:
//                    mBluetoothLeService.writeCharacteristic(mModeCharacteristic,  1);
//                    break;
//                case V10_STATE:
//                    mBluetoothLeService.writeCharacteristic(mModeCharacteristic,  2);
//                    break;
//                default:
//                    break;
//            }
//        }
//    };

    public void exitOnClick(View v){
        // Disconnect and return to intro page
        AlertDialog.Builder builder = new AlertDialog.Builder(BabyLocator.this);
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
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void dimOnClick(View v){
        // switch according to dim state
        switch (dimState) {
            case FULL_DIM:
                dimState = THREEQUARTER_DIM;
                break;
            case THREEQUARTER_DIM:
                dimState = HALF_DIM;
                break;
            case HALF_DIM:
                dimState = QUARTER_DIM;
                break;
            case QUARTER_DIM:
                dimState = EMPTY_DIM;
                break;
            case EMPTY_DIM:
            default:
                dimState = FULL_DIM;
                break;
        }
        value.setTextColor(dimState);
        units.setTextColor(dimState);
    }

    public void holdOnClick(View v){
        // if the multimeter is off ignore hold press
        if (mIsOff) {
            Toast.makeText(getApplicationContext(), "Multimeter is off", Toast.LENGTH_SHORT).show();
        }
        else {
            // cancel hold if hold is active otherwise make hold active
            if (isHold) {
                isHold = false;
                hold.setTextColor(Color.WHITE);
                // subscribe to notifications again
                mBluetoothLeService.setCharacteristicNotification(mMeasurementCharacteristic, true);
                // set units according to state (state can be changed during halt)
//                switch (knob.getState()) {
//                    case R_STATE:
//                        units.setText("Ω");
//                        break;
//                    case MA500_STATE:
//                        units.setText("mA");
//                        break;
//                    case OFF_STATE:
//                        units.setText("");
//                        // deal
//                        break;
//                    case V3_STATE:
//                    case V10_STATE:
//                        units.setText("V");
//                        break;
//                    default:
//                        break;
//                }
            } else {
                // save the value at the time of the pressing
                CharSequence holdValue = value.getText();
                CharSequence holdUnits = units.getText();
                isHold = true;
                hold.setTextColor(Color.RED);
                //unsubscribe notifications
                mBluetoothLeService.setCharacteristicNotification(mMeasurementCharacteristic, false);
                // make sure hold values appear
                value.setText(holdValue);
                units.setText(holdUnits);
            }
        }
    }

    int getPressure(){
        return 14;
    }
}
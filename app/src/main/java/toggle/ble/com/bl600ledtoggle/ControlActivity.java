package toggle.ble.com.bl600ledtoggle;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import java.util.Arrays;
import java.util.List;
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
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import java.util.UUID;

/**
 * This Activity receives a Bluetooth device address provides the user interface to connect, display data, and display GATT services
 * and characteristics supported by the device. The Activity communicates with {@code BluetoothLeService}, which in turn
 * interacts with the Bluetooth LE API.
 */
public class ControlActivity extends AppCompatActivity {

    private final static String TAG = ControlActivity.class.getSimpleName();      //Get name of activity to tag debug and warning messages

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";                      //Name passed by intent that lanched this activity
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";                //MAC address passed by intent that lanched this activity

    private static final String LED_SERVICE = "cdea40a1-dcdb-42bb-8557-5c3d7d5135cb"; //Private service for LED Toggle
    private static final String LED_TOGGLE = "f7552729-9d2c-45cc-ba33-a3327a3bb6d0"; //Characteristic for LED Toggle, properties - notify, write, read
    private static final String CHARACTERISTIC_NOTIFICATION_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";	//Special UUID for descriptor needed to enable notifications

    private BluetoothAdapter mBluetoothAdapter;                                         //BluetoothAdapter controls the Bluetooth radio in the phone
    private BluetoothGatt mBluetoothGatt;                                               //BluetoothGatt controls the Bluetooth communication link
    private BluetoothGattCharacteristic mLedCharacteristic;                             //The BLE characteristic used for LED_TOGGLE data transfers
    private Handler mHandler;                                                           //Handler used to send die roll after a time delay

    private TextView mConnectionState, txtLedState, txtLedStateRead;                                      //TextViews to show connection state and die roll number on the display
    private Button toggleLed, readLed;                                                        //Button to initiate a roll of the die

    private String mDeviceName, mDeviceAddress;                                         //Strings for the Bluetooth device name and MAC address
    private boolean mConnected = false;                                                 //Indicator of an active Bluetooth connection
    private boolean writeComplete = false;                                              //Indicator that the characteristic write has completed (for reference - not used)


    // ----------------------------------------------------------------------------------------------------------------
    // Activity launched
    // Invoked by Intent in onListItemClick method in DeviceScanActivity
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);

        final Intent intent = getIntent();                                              //Get the Intent that launched this activity
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);                        //Get the BLE device name from the Intent
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);                  //Get the BLE device address from the Intent
        mHandler = new Handler();                                                       //Create Handler to delay sending first roll after new connection

        ((TextView) findViewById(R.id.txtDeviceAddress)).setText(mDeviceAddress);          //Display device address on the screen
        mConnectionState = (TextView) findViewById(R.id.txtConnectionState);               //TextView that will display the connection state
        txtLedState = (TextView) findViewById(R.id.txtLedState);
        txtLedStateRead = (TextView) findViewById(R.id.txtLedStateRead);
        toggleLed = (Button) findViewById(R.id.btnToggleLed);                        //Button that will roll the die when clicked
        toggleLed.setOnClickListener(toggleLedClickListener);                     //Set onClickListener for when button is pressed
        readLed = (Button) findViewById(R.id.btnReadLed);
        readLed.setOnClickListener(readLedClickListener);

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE); //Get the BluetoothManager
        mBluetoothAdapter = bluetoothManager.getAdapter();                              //Get a reference to the BluetoothAdapter (radio)
        if (mBluetoothAdapter == null) {                                                //Check if we got the BluetoothAdapter
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show(); //Message that Bluetooth is not supported
            finish();                                                                   //End the activity
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Activity resumed
    @Override
    protected void onResume() {
        super.onResume();

        if (mBluetoothAdapter == null || mDeviceAddress == null) {                      //Check that we still have a Bluetooth adappter and device address
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");     //Warn that something went wrong
            finish();                                                                   //End the Activity
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mDeviceAddress); //Get the Bluetooth device by referencing its address
        if (device == null) {                                                           //Check whether a device was returned
            Log.w(TAG, "Device not found.  Unable to connect.");                        //Warn that something went wrong
            finish();                                                                   //End the Activity
        }
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);                //Directly connect to the device so autoConnect is false
        Log.d(TAG, "Trying to create a new connection.");
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Activity paused
    @Override
    protected void onPause() {
        super.onPause();
        mBluetoothGatt.disconnect();                                                    //Activity is ending so disconnect from Bluetooth device
        mBluetoothGatt.close();                                                         //Close the connection
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Activity is ending
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBluetoothGatt.disconnect();                                                    //Activity is ending so disconnect from Bluetooth device
        mBluetoothGatt.close();                                                         //Close the connection
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Options menu is different depending on whether connected or not
    // Show Connect option if not connected or show Disconnect option if we are connected
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);                          //Show the Options menu
        if (mConnected) {                                                               //See if connected
           menu.findItem(R.id.menu_connect).setVisible(false);                         // then dont show disconnect option
           menu.findItem(R.id.menu_disconnect).setVisible(true);                       // and do show connect option
        }
        else {                                                                          //If not connected
            menu.findItem(R.id.menu_connect).setVisible(true);                          // then show connect option
            menu.findItem(R.id.menu_disconnect).setVisible(false);                      // and don't show disconnect option
        }
        return true;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Menu item selected
    // Connect or disconnect to BLE device
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {                                                     //Get which menu item was selected
            case R.id.menu_connect:                                                     //Option to Connect chosen
                if(mBluetoothGatt != null) {                                            //If there is a valid GATT connection
                    mBluetoothGatt.connect();                                           // then connect
                }
                return true;
            case R.id.menu_disconnect:                                                  //Option to Disconnect chosen
                if(mBluetoothGatt != null) {                                            //If there is a valid GATT connection
                    mBluetoothGatt.disconnect();                                        // then disconnect
                }
                return true;
            case android.R.id.home:                                                     //Option to go back was chosen
                onBackPressed();                                                        //Execute functionality of back button
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Update text with connection state
    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);                                   //Update text to say "Connected" or "Disconnected"
                txtLedState.setText("0");                                               //Reset LED state to 0 when connection changes
            }
        });
    }

    private void setLedState(final String state) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLedCharacteristic.setValue(state);                     //Set value of LED characteristic to send new value
                writeCharacteristic(mLedCharacteristic);               //Call method to write the characteristic
                txtLedState.setText(state);                             //Set the LED state text to show new value
            }
        });
    }

    private void updateLedState(final String state) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtLedState.setText(state);                       //Set the LED state text to show new value
            }
        });
    }
    private void updateReadLedState(final String state) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtLedStateRead.setText(state);                       //Set the LED state text to show new value
            }
        });
    }


    // ----------------------------------------------------------------------------------------------------------------
    // Listener for the toggle LED button
    private final Button.OnClickListener toggleLedClickListener = new Button.OnClickListener() {

        public void onClick(View view) {                                                //Button was clicked
            if(txtLedState.getText().toString().equals("0")) {
                setLedState("1");                                                           //Update the state of the die with a new roll and send over BLE
            }
            else {
                setLedState("0");                                                           //Update the state of the die with a new roll and send over BLE
            }
            toggleLed.setEnabled(false); //will be re-enabled when state has been set successfully
        }
    };

    // ----------------------------------------------------------------------------------------------------------------
    // Listener for the read LED button
    private final Button.OnClickListener readLedClickListener = new Button.OnClickListener() {

        public void onClick(View view) {                                                //Button was clicked
            readLed.setEnabled(false); //will be re-enabled when state has been set successfully
            readCharacteristic(mLedCharacteristic);               //Call method to write the characteristic
        }
    };


    // ----------------------------------------------------------------------------------------------------------------
    // Iterate through the supported GATT Services/Characteristics to see if the MLDP srevice is supported
    private void findGattService(List<BluetoothGattService> gattServices) {
        if (gattServices == null) {                                                     //Verify that list of GATT services is valid
            Log.d(TAG, "findGattService found no Services");
            return;
        }
        String uuid;                                                                    //String to compare received UUID with desired known UUIDs
        mLedCharacteristic = null;                                                               //Searching for a characteristic, start with null value

        for (BluetoothGattService gattService : gattServices) {                         //Test each service in the list of services
            uuid = gattService.getUuid().toString();                                    //Get the string version of the service's UUID
            if (uuid.equals(LED_SERVICE)) {                                    //See if it matches the UUID of the LED service
                List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics(); //If so then get the service's list of characteristics
                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) { //Test each characteristic in the list of characteristics
                    uuid = gattCharacteristic.getUuid().toString();                     //Get the string version of the characteristic's UUID
                    if (uuid.equals(LED_TOGGLE)) {                          //See if it matches the UUID of the LED_TOGGLE data characteristic
                        mLedCharacteristic = gattCharacteristic;                                 //If so then save the reference to the characteristic
                        Log.d(TAG, "Found LED toggle characteristics");
                    }
                    final int characteristicProperties = gattCharacteristic.getProperties(); //Get the properties of the characteristic
                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_NOTIFY)) > 0) { //See if the characteristic has the Notify property
                        mBluetoothGatt.setCharacteristicNotification(gattCharacteristic, true); //If so then enable notification in the BluetoothGatt
                        BluetoothGattDescriptor descriptor = gattCharacteristic.getDescriptor(UUID.fromString(CHARACTERISTIC_NOTIFICATION_CONFIG)); //Get the descripter that enables notification on the server
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE); //Set the value of the descriptor to enable notification
                        mBluetoothGatt.writeDescriptor(descriptor);                     //Write the descriptor
                    }
                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_INDICATE)) > 0) { //See if the characteristic has the Indicate property
                        mBluetoothGatt.setCharacteristicNotification(gattCharacteristic, true); //If so then enable notification (and indication) in the BluetoothGatt
                        BluetoothGattDescriptor descriptor = gattCharacteristic.getDescriptor(UUID.fromString(CHARACTERISTIC_NOTIFICATION_CONFIG)); //Get the descripter that enables indication on the server
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE); //Set the value of the descriptor to enable indication
                        mBluetoothGatt.writeDescriptor(descriptor);                     //Write the descriptor
                    }
                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE)) > 0) { //See if the characteristic has the Write (acknowledged) property
                        gattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT); //If so then set the write type (write with acknowledge) in the BluetoothGatt
                    }
                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0) { //See if the characteristic has the Write (unacknowledged) property
                        gattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE); //If so then set the write type (write with no acknowledge) in the BluetoothGatt
                    }
                }
                break;                                                                  //Found the LED service and are not looking for any other services
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Implements callback methods for GATT events that the app cares about.  For example: connection change and services discovered.
    // When onConnectionStateChange() is called with newState = STATE_CONNECTED then it calls mBluetoothGatt.discoverServices()
    // resulting in another callback to onServicesDiscovered()
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) { //Change in connection state
            if (newState == BluetoothProfile.STATE_CONNECTED) {                         //See if we are connected
                Log.i(TAG, "Connected to GATT server.");
                mConnected = true;                                                      //Record the new connection state
                updateConnectionState(R.string.connected);                              //Update the display to say "Connected"
                invalidateOptionsMenu();                                                //Force the Options menu to be regenerated to show the disconnect option
                mBluetoothGatt.discoverServices();                                      // Attempt to discover services after successful connection.
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {                 //See if we are not connected
                Log.i(TAG, "Disconnected from GATT server.");
                mConnected = false;                                                     //Record the new connection state
                updateConnectionState(R.string.disconnected);                           //Update the display to say "Disconnected"
                invalidateOptionsMenu();                                                //Force the Options menu to be regenerated to show the connect option
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {              //Service discovery complete
            if (status == BluetoothGatt.GATT_SUCCESS && mBluetoothGatt != null) {       //See if the service discovery was successful
                findGattService(mBluetoothGatt.getServices());                          //Get the list of services and call method to look for LED service
            }
            else {                                                                      //Service discovery was not successful
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        //For information only. This application uses Indication to receive updated characteristic data, not Read
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) { //A request to Read has completed
            if (status == BluetoothGatt.GATT_SUCCESS) {                                 //See if the read was successful
                final String dataValue = characteristic.getStringValue(0);                     //Get the value of the characteristic
                Log.d(TAG,"Led state read: "+ dataValue);
                if(characteristic.getUuid().toString().equals(LED_TOGGLE)) {
                    mHandler.postDelayed(new Runnable() { //Re-enable toggle button
                        @Override
                        public void run() {
                            //Do something after 100ms
                            readLed.setEnabled(true);
                            updateReadLedState(dataValue);
                        }
                    }, 100);
                }
            }
            else {
                Log.d(TAG,"Read Failed");
            }
        }

        //For information only. This application sends small packets infrequently and does not need to know what the previous write completed
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) { //A request to Write has completed
            if (status == BluetoothGatt.GATT_SUCCESS) {                                 //See if the write was successful
                writeComplete = true;                                                   //Record that the write has completed
                Log.d(TAG, "Outgoing data: " + characteristic.getStringValue(0));
                mHandler.postDelayed(new Runnable() { //Re-enable toggle button
                    @Override
                    public void run() {
                        //Do something after 100ms
                        toggleLed.setEnabled(true);
                    }
                }, 100);
            }
            else {
                writeComplete = false;
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) { //Indication or notification was received
            String dataValue = characteristic.getStringValue(1);                        //Get the value of the characteristic
            byte[] bytes = characteristic.getValue();
            Log.d(TAG,"Incoming Data "+ dataValue);
            updateLedState(dataValue);
        }
    };

    // ----------------------------------------------------------------------------------------------------------------
    // Request a read of a given BluetoothGattCharacteristic. The Read result is reported asynchronously through the
    // BluetoothGattCallback onCharacteristicRead callback method.
    // For information only. This application uses Indication to receive updated characteristic data, not Read

    private void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {                      //Check that we have access to a Bluetooth radio
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);                              //Request the BluetoothGatt to Read the characteristic
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Write to a given characteristic. The completion of the write is reported asynchronously through the
    // BluetoothGattCallback onCharacteristicWrite callback method.
    private void writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {                      //Check that we have access to a Bluetooth radio
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        int test = characteristic.getProperties();                                      //Get the properties of the characteristic
        if ((test & BluetoothGattCharacteristic.PROPERTY_WRITE) == 0 && (test & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0) { //Check that the property is writable
            return;
        }

        if (mBluetoothGatt.writeCharacteristic(characteristic)) {                       //Request the BluetoothGatt to do the Write
            //The request was accepted, this does not mean the write completed
        }
        else {
            Log.d(TAG, "writeCharacteristic failed");                                   //Write request was not accepted by the BluetoothGatt
        }
    }


}
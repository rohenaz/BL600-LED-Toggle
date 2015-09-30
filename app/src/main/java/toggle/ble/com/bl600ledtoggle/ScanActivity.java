package toggle.ble.com.bl600ledtoggle;

import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class ScanActivity extends ListActivity {
    private final static String TAG = ScanActivity.class.getSimpleName();
    private LeDeviceListAdapter mLeDeviceListAdapter;                                   //List adapter to hold list of BLE devices from a scan
    private BluetoothAdapter mBluetoothAdapter;                                         //BluetoothAdapter represents the radio in the Smartphone
    private boolean mScanning;                                                          //Keep track of whether there is a scan in progress
    private Handler mHandler;                                                           //Handler used to stop scanning after time delay
    private static final int REQUEST_ENABLE_BT = 1;                                     //Constant to identify response from Activity that enables Bluetooth
    private static final long SCAN_PERIOD = 100000;                                      //Length of time in milliseconds to scan for BLE devices

    // ----------------------------------------------------------------------------------------------------------------
    // Activity launched
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //this.getActionBar().setTitle(R.string.title_devices);                           //Display "BLE Device Scan" on the action bar
        mHandler = new Handler();                                                       //Create Handler to stop scanning

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) { //Check if BLE is supported
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show(); //Message that BLE not supported
            finish();                                                                   //End the app
        }

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE); //Get the BluetoothManager
        mBluetoothAdapter = bluetoothManager.getAdapter();                              //Get a reference to the BluetoothAdapter (radio)

        if (mBluetoothAdapter == null) {                                                //Check if we got the BluetoothAdapter
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show(); //Message that Bluetooth not supported
            finish();                                                                   //End the app
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Activity resumed
    // Enable BT if not already enabled, initialize list of BLE devices, start scan for BLE devices
    @Override
    protected void onResume() {
        super.onResume();

        if (!mBluetoothAdapter.isEnabled()) {                                           //Check if BT is not enabled
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE); //Create an intent to get permission to enable BT
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);                  //Fire the intent to start the activity that will return a result based on user response
        }

        mLeDeviceListAdapter = new LeDeviceListAdapter();                               //Create new list adapter to hold list of BLE devices found during scan
        setListAdapter(mLeDeviceListAdapter);                                           //Bind our ListActivity to the new list adapter
        scanLeDevice(true);                                                             //Start scanning for BLE devices
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Activity paused
    // Stop scan and clear device list
    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);                                                            //Stop scanning for BLE devices
        mLeDeviceListAdapter.clear();                                                   //Clear the list of BLE devices found during the scan
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Options menu is different depending on whether scanning or not
    // Show Scan option if not scanning or show Stop option if we are scanning
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.scan_start_stop, menu);                        //Show the Options menu
        if (!mScanning) {                                                               //See if not scanning
            menu.findItem(R.id.menu_stop).setVisible(false);                            //  hide Stop scan menu option
            menu.findItem(R.id.menu_scan).setVisible(true);                             //  and show Scan menu option
        } else {                                                                          //Else are scanning
            menu.findItem(R.id.menu_stop).setVisible(true);                             //  show Stop menu option
            menu.findItem(R.id.menu_scan).setVisible(false);                            //  and hide Scan menu option
            menu.findItem(R.id.menu_refresh).setActionView(R.layout.indeterminate_progress); //Show progress indicator on Action Bar
        }
        return true;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Menu item selected
    // Start or stop scanning for BLE devices
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {                                                     //Get which menu item was selected
            case R.id.menu_scan:                                                        //Option to Scan chosen
                mLeDeviceListAdapter.clear();                                           //Clear list of BLE devices found
                scanLeDevice(true);                                                     //Start scanning
                break;
            case R.id.menu_stop:                                                        //Option to Stop scanning chosen
                scanLeDevice(false);                                                    //Stop scanning
                break;
        }
        return true;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Test response from request to enable BT adapter in case user did not enable
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) { //User chose not to enable Bluetooth.
            finish();                                                                   //Destroy the activity - end the application
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);                          //Pass the activity result up to the parent method
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Device selected in list adapter
    // Start DeviceControlActivity and pass the BLE device name and address to the activity
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);        //Get the Bluetooth device from the list adapter
        if (device == null)                                                             //Ignore if device is not valid
            return;
        final Intent intent = new Intent(this, ControlActivity.class);            //Create Intent to start the DeviceControlActivity
        intent.putExtra(ControlActivity.EXTRAS_DEVICE_NAME, device.getName());    //Add BLE device name to the intent (for info, not needed)
        intent.putExtra(ControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress()); //Add BLE device address to the intent
        if (mScanning) {                                                                //See if still scanning
            mBluetoothAdapter.stopLeScan(mLeScanCallback);                              //Stop the scan in progress
            mScanning = false;                                                          //Indicate that we are not scanning
        }
        startActivity(intent);                                                          //Start the DeviceControlActivity
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Scan for BLE device for SCAN_PERIOD milliseconds.
    // The mLeScanCallback method is called each time a device is found during the scan
    public void scanLeDevice(final boolean enable) {
        if (enable) {                                                                   //Method was called with option to start scanning
            mHandler.postDelayed(new Runnable() {                                       //Create delayed runnable that will stop the scan when it runs after SCAN_PERIOD milliseconds
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(),"Scan Stopped",Toast.LENGTH_LONG).show();
                    mScanning = false;                                                  //Indicate that we are not scanning - used for menu Stop/Scan context
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);                      //Stop scanning - callback method indicates which scan to stop
                    invalidateOptionsMenu();                                            //Indicate that the options menu has changed, so should be recreated.
                }
            }, SCAN_PERIOD);
            Toast.makeText(this,"Scanning for Devices",Toast.LENGTH_LONG).show();
            mScanning = true;                                                           //Indicate that we are busy scanning - used for menu Stop/Scan context
            mBluetoothAdapter.startLeScan(mLeScanCallback);                             //Start scanning with callback method to execute when a new BLE device is found
        } else {                                                                          //Method was called with option to stop scanning
            mScanning = false;                                                          //Indicate that we are not scanning - used for menu Stop/Scan context
            Toast.makeText(this,"Scan Stopped",Toast.LENGTH_LONG).show();
            mBluetoothAdapter.stopLeScan(mLeScanCallback);                              //Stop scanning - callback method indicates which scan to stop
        }
        invalidateOptionsMenu();                                                        //Indicate that the options menu has changed, so should be recreated.
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Device scan callback. Bluetooth adapter calls this method when a new device is discovered during a scan.
    private final BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) { //Android calls method with Bluetooth device advertising information
            runOnUiThread(new Runnable() {                                              //Create runnable that will add the device to the list adapter
                @Override
                public void run() {
                    mLeDeviceListAdapter.addDevice(device);                             //Add the device to the list adapter that will show all the available devices
                    Log.d(TAG, "Found BLE Device: " + device.getAddress().toString()); //Debug information to log the devices as they are found
                    mLeDeviceListAdapter.notifyDataSetChanged();                        //Tell the list adapter that it needs to refresh the view
                }
            });
        }
    };

    // ----------------------------------------------------------------------------------------------------------------
    // Adapter for holding devices found through scanning. Acts as a bridge between the list data and the view deiplaying the data
    private class LeDeviceListAdapter extends BaseAdapter {
        private final ArrayList<BluetoothDevice> mLeDevices;                            //List of Bluetooth devices found during scan
        private final LayoutInflater mInflator;                                         //Layout inflator to display information about each device

        public LeDeviceListAdapter() {                                                  //Constructor initializes the list
            super();
            mLeDevices = new ArrayList<BluetoothDevice>();                              //New arrayliat to hold the Bluetooth devices
            mInflator = ScanActivity.this.getLayoutInflater();                    //Get the layout inflator associated with this activity
        }

        public void addDevice(BluetoothDevice device) {                                 //Method to add device to list
            if (!mLeDevices.contains(device)) {                                          //First check that it is a new device not in the list
                mLeDevices.add(device);                                                 //Add the device to the list
            }
        }

        public BluetoothDevice getDevice(int position) {                                //Method to get device from list
            return mLeDevices.get(position);                                            //Return the device at the selected position
        }

        public void clear() {                                                           //Method to clear the list
            mLeDevices.clear();
        }

        @Override
        public int getCount() {                                                         //Method to get number of devices in the list
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {                                                  //Method to get generic object from the list - Not used
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {                                                  //Method to get object's ID from the list - Not used
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {                    //Method to get view of device and show on the screen
            ViewHolder viewHolder;                                                      //Declare the structure to hold TextViews to show the name and address that will be displayed
            // General ListView optimization code.
            if (view == null) {                                                         //Check that we were passed a reference for a new view
                view = mInflator.inflate(R.layout.listitem_device, null);               //Put the list item (from XML layout file) on the screen
                viewHolder = new ViewHolder();                                          //New object to hold references to which TextViews hold the name and address of the device
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address); //Get reference to TextView for the address
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name); //Get reference to TextView for the name
                view.setTag(viewHolder);                                                //Tag the view with the ViewHolder
            } else {
                viewHolder = (ViewHolder) view.getTag();                                //View already exists so get the ViewHolder that was used tag the view
            }

            BluetoothDevice device = mLeDevices.get(i);                                 //Get the device associated with this view
            final String deviceName = device.getName();                                 //Get the name of the device
            if (deviceName != null && deviceName.length() > 0) {                        //Check that the name is valid (name is not required in the BLE advertising packet)
                viewHolder.deviceName.setText(deviceName);                              //If so show it on the screen in the TextView
            } else {
                viewHolder.deviceName.setText(R.string.unknown_device);                 //If name is invalid, put "Unknown Device" on the screen
            }                                                                           //(this happens if the advertisement packet does not contain a name)
            viewHolder.deviceAddress.setText(device.getAddress());                      //Print the MAC address on the screen
            return view;
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Class to hold device name and address
    static class ViewHolder {
        TextView deviceName;                                                            //TextView to show device name in the list on the screen
        TextView deviceAddress;                                                         //TextView to show device address in the list on the screen
    }
}

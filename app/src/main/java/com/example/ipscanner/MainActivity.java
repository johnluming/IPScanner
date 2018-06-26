package com.example.ipscanner;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.Formatter;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final int MIN_TEST_ADDRESS = 1;
    private static final int MAX_TEST_ADDRESS = 254;
    // we skip the test of this device's ip
    private static final int ADDRESSES_TO_TEST = MAX_TEST_ADDRESS - MIN_TEST_ADDRESS;
    private static final int TEST_TIME_OUT_IN_MILLIS = 1000;

    /* create a custom thread pool executor to execute async tasks in parallel */
    // private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = 256; //default is Math.max(2, Math.min(CPU_COUNT - 1, 4));
    private static final int MAXIMUM_POOL_SIZE = CORE_POOL_SIZE * 2; // default is CPU_COUNT * 2 + 1
    private static final int KEEP_ALIVE_SECONDS = 30;
    private static final BlockingQueue<Runnable> sPoolWorkQueue =
            new LinkedBlockingQueue<Runnable>(12800); //default is 128
    private static final ThreadPoolExecutor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS, sPoolWorkQueue);

    private List<NetworkDevice> mNetworkDeviceList = new ArrayList<>();
    private AdapterNetworkDevice mAdapterNetworkDevice;
    private ProgressBar mProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // attach adapter to recycler view
        RecyclerView mRecyclerViewNetworkDevice = findViewById(R.id.recyclerViewNetworkDevice);
        mRecyclerViewNetworkDevice.setLayoutManager(new LinearLayoutManager(this));
        mAdapterNetworkDevice = new AdapterNetworkDevice(mNetworkDeviceList);
        mRecyclerViewNetworkDevice.setAdapter(mAdapterNetworkDevice);

        mProgressBar = findViewById(R.id.progressBar);
        mProgressBar.setMax(ADDRESSES_TO_TEST);
        mProgressBar.setVisibility(View.INVISIBLE);
    }

    private boolean mIsScanning = false;

    /**
     * show and hide menu items base on boolean variable mIsScanning
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);

        MenuItem menuItemRefresh = menu.findItem(R.id.menu_item_refresh);
        MenuItem menuItemStop = menu.findItem(R.id.menu_item_stop);
        menuItemRefresh.setVisible(!mIsScanning);
        menuItemStop.setVisible(mIsScanning);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_refresh:
                onRefresh();
                return true;
            case R.id.menu_item_stop:
                onStopScanning();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // region private methods

    private int mTestsCount = 0;

    /**
     * called when user clicks the refresh button
     */
    private void onRefresh() {
        mIsScanning = true;
        invalidateOptionsMenu();

        mProgressBar.setProgress(0);
        mProgressBar.setVisibility(View.VISIBLE);

        mNetworkDeviceList.clear();
        mTestsCount = 0;

        // add this device to the NetworkDevice list and refresh adapter
        WifiManager wm = (WifiManager) getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        assert wm != null;
        WifiInfo wifiInfo = wm.getConnectionInfo();
        int wifiIpAddress = wifiInfo.getIpAddress();
        String wifiIpString = Formatter.formatIpAddress(wifiIpAddress);
        String wifiMacAddress = getMacAddress();
        mNetworkDeviceList.add(new NetworkDevice(wifiIpString, wifiMacAddress));
        mAdapterNetworkDevice.notifyDataSetChanged();

        // scan for reachable ips
        String addressPrefix = wifiIpString.substring(0, wifiIpString.lastIndexOf(".") + 1);
        for (int i = MIN_TEST_ADDRESS; i <= MAX_TEST_ADDRESS; i++) {
            String testIpAddressString = addressPrefix + Integer.toString(i);

            // skip test of this device's ip
            if (testIpAddressString.equals(wifiIpString))
                continue;

            new TaskTestIsIpReachable(this).executeOnExecutor(
                    THREAD_POOL_EXECUTOR, testIpAddressString);
        }

        // while other async tasks are scanning for reachable ips, read the arp table
        new TaskGetMapIpToMac(this).execute();
    }

    /**
     * called when user clicks the stop button
     */
    private void onStopScanning() {
        mIsScanning = false;
        invalidateOptionsMenu();
        mProgressBar.setVisibility(View.INVISIBLE);
    }

    // endregion

    // region static methods

    static boolean macAddressIsNotAllZeros(String address) {
        return !address.equals("00:00:00:00:00:00");
    }

    // source: https://stackoverflow.com/questions/33103798/how-to-get-wi-fi-mac-address-in-android-marshmallow
    static String getMacAddress() {
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (!nif.getName().equalsIgnoreCase("wlan0")) continue;

                byte[] macBytes = nif.getHardwareAddress();
                if (macBytes == null) {
                    return "";
                }

                StringBuilder res1 = new StringBuilder();
                for (byte b : macBytes) {
                    res1.append(String.format("%02X:", b));
                }

                if (res1.length() > 0) {
                    res1.deleteCharAt(res1.length() - 1);
                }
                return res1.toString();
            }
        } catch (Exception ex) {
        }
        return "02:00:00:00:00:00";
    }

    static Comparator<NetworkDevice> NetworkDeviceComparator = new Comparator<NetworkDevice>() {
        @Override
        public int compare(NetworkDevice device1, NetworkDevice device2) {
            String ipNumberString1 = device1.getIpString().substring(
                    device1.getIpString().lastIndexOf(".") + 1);
            String ipNumberString2 = device2.getIpString().substring(
                    device2.getIpString().lastIndexOf(".") + 1);
            return Integer.parseInt(ipNumberString1) - Integer.parseInt(ipNumberString2);
        }
    };

    // endregion

    // region async tasks

    private Map<String, String> mMapIpToMac = new HashMap<>();

    static class TaskTestIsIpReachable extends AsyncTask<String, Void, String> {

        private WeakReference<MainActivity> activityReference;

        TaskTestIsIpReachable(MainActivity activity) {
            activityReference = new WeakReference<>(activity);
        }

        @Override
        protected String doInBackground(String... ipAddresses) {

            MainActivity activity = activityReference.get();
            if (activity == null || activity.isFinishing() || !activity.mIsScanning) {
                return null;
            }

            try {
                InetAddress testIpAddress = InetAddress.getByName(ipAddresses[0]);
                boolean reachable = testIpAddress.isReachable(TEST_TIME_OUT_IN_MILLIS);

                if (reachable) {
                    return ipAddresses[0];
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(String ipString) {
            MainActivity mainActivity = activityReference.get();
            if (mainActivity == null || mainActivity.isFinishing() || !mainActivity.mIsScanning) {
                return;
            }

            mainActivity.mTestsCount++;

            // add new NetworkDevice containing a reachable ip address to the NetworkDevice list,
            // sort it and then refresh the adapter
            if (ipString != null) {
                String macAddress = null;

                // check if the at the moment the ip-mac map contains the ip address,
                // the map is still being continuously updated, when it is fully updated,
                // we will read it again to get mac addresses of interests
                if (mainActivity.mMapIpToMac.containsKey(ipString)) {

                    String macAddressInMap = mainActivity.mMapIpToMac.get(ipString);
                    if (MainActivity.macAddressIsNotAllZeros(macAddressInMap)) {
                        macAddress = macAddressInMap;
                    }
                }

                mainActivity.mNetworkDeviceList.add(
                        new NetworkDevice(ipString, macAddress));
                Collections.sort(mainActivity.mNetworkDeviceList, MainActivity.NetworkDeviceComparator);
                mainActivity.mAdapterNetworkDevice.notifyDataSetChanged();
            }

            // update progress bar
            mainActivity.mProgressBar.setProgress(mainActivity.mTestsCount);

            if (mainActivity.mTestsCount == ADDRESSES_TO_TEST) {
                mainActivity.mProgressBar.setVisibility(View.INVISIBLE);
                mainActivity.mIsScanning = false;
                mainActivity.invalidateOptionsMenu();
            }
        }
    }

    static class TaskGetMapIpToMac extends AsyncTask<Void, Void, Void> {

        private WeakReference<MainActivity> activityReference;

        TaskGetMapIpToMac(MainActivity activity) {
            activityReference = new WeakReference<>(activity);
        }

        @Override
        protected Void doInBackground(Void... voids) {

            MainActivity mainActivity = activityReference.get();
            if (mainActivity == null || mainActivity.isFinishing() || !mainActivity.mIsScanning) {
                return null;
            }

            mainActivity.mMapIpToMac.clear();

            BufferedReader localBufferedReader = null;
            try {
                localBufferedReader = new BufferedReader(new FileReader(new File("/proc/net/arp")));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            String line;

            try {
                if (localBufferedReader != null) {
                    while ((line = localBufferedReader.readLine()) != null) {
                        String[] ipmac = line.split("[ ]+");
                        if (!ipmac[0].matches("IP")) {
                            String ip = ipmac[0];
                            String mac = ipmac[3].toUpperCase();
                            mainActivity.mMapIpToMac.put(ip, mac);

                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            MainActivity mainActivity = activityReference.get();
            if (mainActivity == null || mainActivity.isFinishing() || !mainActivity.mIsScanning) {
                return;
            }

            addMacAddressesToNetworkDeviceList(mainActivity.mMapIpToMac, mainActivity.mNetworkDeviceList);

            mainActivity.mAdapterNetworkDevice.notifyDataSetChanged();
        }

        private static void addMacAddressesToNetworkDeviceList(Map<String, String> mapIpToMac,
                                                               List<NetworkDevice> networkDeviceList) {
            for (NetworkDevice networkDevice : networkDeviceList) {
                String key = networkDevice.getIpString();
                if (mapIpToMac.containsKey(key))
                    networkDevice.setMacString(mapIpToMac.get(key));
            }
        }

    }

    // endregion
}



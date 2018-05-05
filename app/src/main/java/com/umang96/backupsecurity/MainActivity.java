package com.umang96.backupsecurity;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    static Thread watchThread;
    static boolean visible = false;
    Button setFileScanInterval, setNetworkScanInterval, scanWifiNetworks, setSmsKeyword, ftpServer;
    EditText fileScanInterval, networkScanInterval, smsKeyword;
    CheckBox enableBackup, enableSecurity;
    TextView preferredNetwork, ftpStatus, ftpUrl;
    List<ScanResult> mScanResults;
    private WifiManager mWifiManager;
    private final BroadcastReceiver mWifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }
            if (intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                mScanResults = mWifiManager.getScanResults();
                Log.d(TAG, "results = " + mScanResults.toString() + " n = " + mScanResults.size());
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (checkLocationPermission()) {
            prepareWifiList();
        }
        if (!checkStoragePermission()) {
            requestSdWrite();
        }
        if (!BackgroundService.serviceRunning) {
            Log.d(TAG, "starting service, got false");
            startService(new Intent(MainActivity.this, BackgroundService.class));
        } else {
            Log.d(TAG, "not starting, got true");
        }
        loadWidgetIds();
        loadValues();
        setWidgets();
        startWatchThread();
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        if (!watchThread.isAlive()) {
            Log.d(TAG, "onResume called and thread is not alive");
            startWatchThread();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        visible = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mWifiScanReceiver);
        visible = false;
    }

    private void loadWidgetIds() {
        setFileScanInterval = findViewById(R.id.btn_fileScanInterval);
        setNetworkScanInterval = findViewById(R.id.btn_networkScanInterval);
        scanWifiNetworks = findViewById(R.id.btn_scan);
        fileScanInterval = findViewById(R.id.et_fileScanInterval);
        networkScanInterval = findViewById(R.id.et_networkScanInterval);
        smsKeyword = findViewById(R.id.et_smsKeyword);
        enableBackup = findViewById(R.id.cb_enableBackupService);
        enableSecurity = findViewById(R.id.cb_enableSecurityService);
        preferredNetwork = findViewById(R.id.tv_preferredNetworkValue);
        setSmsKeyword = findViewById(R.id.btn_smsKeyword);
        ftpStatus = findViewById(R.id.tv_ftpStatus);
        ftpServer = findViewById(R.id.btn_ftpServer);
        ftpUrl = findViewById(R.id.tv_ftpUrl);
    }

    private void loadValues() {
        SharedPreferences sp = getSharedPreferences("prefs_main", MODE_PRIVATE);
        enableBackup.setChecked(sp.getBoolean("enableBackup", false));
        enableSecurity.setChecked(sp.getBoolean("enableSecurity", false));
        String fsi = "" + sp.getInt("fileScanInterval", 60);
        fileScanInterval.setText(fsi);
        String nsi = "" + sp.getInt("networkScanInterval", 30);
        networkScanInterval.setText(nsi);
        smsKeyword.setText(sp.getString("smsKeyword", "asdfclkjn23145890"));
        preferredNetwork.setText(sp.getString("preferredNetwork", "not set"));
        String ftps = "Status     :     " + (BackgroundService.serverRunning ? "running" : "stopped");
        ftpStatus.setText(ftps);
        ftpServer.setText(BackgroundService.serverRunning ? "Stop" : "Start");
        String ftpu = "Ftp url     :     " + "ftp://" + getWifiAddress() + ":12345/";
        ftpUrl.setText(ftpu);
        ftpUrl.setVisibility(BackgroundService.serverRunning ? View.VISIBLE : View.GONE);
    }

    private void setWidgets() {
        setFileScanInterval.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (fileScanInterval.getText() == null || fileScanInterval.getText().length() < 1) {
                    Toast.makeText(MainActivity.this, "File scan interval cannot be empty", Toast.LENGTH_SHORT).show();
                } else {
                    SharedPreferences.Editor et = getSharedPreferences("prefs_main", MODE_PRIVATE).edit();
                    et.putInt("fileScanInterval", Integer.parseInt(fileScanInterval.getText().toString()));
                    et.apply();
                }
            }
        });

        setNetworkScanInterval.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (networkScanInterval.getText() == null || networkScanInterval.getText().length() < 1) {
                    Toast.makeText(MainActivity.this, "Network scan interval cannot be empty", Toast.LENGTH_SHORT).show();
                } else {
                    SharedPreferences.Editor et = getSharedPreferences("prefs_main", MODE_PRIVATE).edit();
                    et.putInt("networkScanInterval", Integer.parseInt(networkScanInterval.getText().toString()));
                    et.apply();
                }
            }
        });

        enableBackup.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                SharedPreferences sp = getSharedPreferences("prefs_main", MODE_PRIVATE);
                String prefNetwork = sp.getString("preferredNetwork", "not set");
                if (b) {
                    if (prefNetwork.equals("not set")) {
                        Toast.makeText(MainActivity.this, "Must select preferred network first", Toast.LENGTH_SHORT).show();
                        enableBackup.setChecked(false);
                    } else {
                        SharedPreferences.Editor et = getSharedPreferences("prefs_main", MODE_PRIVATE).edit();
                        et.putBoolean("enableBackup", true);
                        et.apply();
                        startService(new Intent(MainActivity.this, BackgroundService.class));
                    }
                } else {
                    Intent it = new Intent(MainActivity.this, BackgroundService.class);
                    stopService(it);
                    startService(it);
                    SharedPreferences.Editor et = getSharedPreferences("prefs_main", MODE_PRIVATE).edit();
                    et.putBoolean("enableBackup", false);
                    et.apply();
                }
            }
        });

        enableSecurity.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (!checkSmsPermission()) {
                    requestSms();
                }
                if (b) {
                    SharedPreferences.Editor et = getSharedPreferences("prefs_main", MODE_PRIVATE).edit();
                    et.putBoolean("enableSecurity", true);
                    et.apply();
                } else {
                    SharedPreferences.Editor et = getSharedPreferences("prefs_main", MODE_PRIVATE).edit();
                    et.putBoolean("enableSecurity", false);
                    et.apply();
                }
            }
        });

        setSmsKeyword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (smsKeyword.getText() != null && smsKeyword.getText().length() > 10) {
                    SharedPreferences.Editor et = getSharedPreferences("prefs_main", MODE_PRIVATE).edit();
                    et.putString("smsKeyword", smsKeyword.getText().toString());
                    et.apply();
                } else {
                    SharedPreferences sp = getSharedPreferences("prefs_main", MODE_PRIVATE);
                    Toast.makeText(MainActivity.this, "Length must be more than 10 characters", Toast.LENGTH_SHORT).show();
                    smsKeyword.setText(sp.getString("smsKeyword", "asdfclkjn23145890"));
                }
            }
        });

        scanWifiNetworks.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!checkLocationPermission()) {
                    requestLocation();
                }
                prepareWifiList();
                if (mScanResults == null || mScanResults.size() <= 0) {
                    Toast.makeText(MainActivity.this, "No wifi networks found, try again.", Toast.LENGTH_SHORT).show();
                    return;
                }
                AlertDialog.Builder builderSingle = new AlertDialog.Builder(MainActivity.this);
                builderSingle.setTitle("Select one network -");

                final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.select_dialog_singlechoice);
                for (ScanResult sr : mScanResults) {
                    arrayAdapter.add(sr.SSID);
                }

                builderSingle.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

                builderSingle.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String strName = arrayAdapter.getItem(which);
                        Log.d(TAG, "you selected " + strName);
                        SharedPreferences.Editor et = getSharedPreferences("prefs_main", MODE_PRIVATE).edit();
                        et.putString("preferredNetwork", strName);
                        et.apply();
                        preferredNetwork.setText(strName);
                    }
                });
                builderSingle.show();
            }
        });

        ftpServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SharedPreferences sp = getSharedPreferences("prefs_main", MODE_PRIVATE);
                if (sp.getBoolean("enableBackup", false)) {
                    Toast.makeText(MainActivity.this, "Please turn off backup service first !", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!BackgroundService.serviceRunning) {
                    startService(new Intent(MainActivity.this, BackgroundService.class));
                }
                if (BackgroundService.serverRunning) {
                    sendBroadcast(new Intent(BackgroundService.STOP_FTP));
                } else {
                    if (checkWifi()) {
                        sendBroadcast(new Intent(BackgroundService.START_FTP));
                    } else {
                        Toast.makeText(MainActivity.this, "Not on wifi network !", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

    }

    private synchronized void prepareWifiList() {
        registerReceiver(mWifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        mWifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        assert mWifiManager != null;
        mWifiManager.startScan();
    }

    private boolean checkStoragePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestSdWrite() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
    }

    private boolean checkLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocation() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
    }

    private boolean checkSmsPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestSms() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECEIVE_SMS}, 0);
    }

    private void startWatchThread() {
        visible = true;
        watchThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (visible) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            loadValues();
                        }
                    });
                    try {
                        Log.d(TAG, "watchThread is running id = " + Thread.currentThread().getId());
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        watchThread.start();
    }

    private boolean checkWifi() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo currentNetwork = null;
        //  Guard NullPointerException
        if (connectivityManager != null) {
            currentNetwork = connectivityManager.getActiveNetworkInfo();
        }
        //  Return true if connected on WiFi
        return currentNetwork != null && currentNetwork.getType() == ConnectivityManager.TYPE_WIFI;

    }

    private String getWifiAddress() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        int ipAddress = -1;
        //  Guard NullPointerException
        if (wifiManager != null) {
            ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        }

        //  Convert to big-endian if needed
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();
        String ipAddressString = "";
        try {
            ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
        } catch (UnknownHostException e) {
            Log.e(TAG, "showAddress: Unable to get host address");
            e.printStackTrace();
        }

        //  Return Wifi IP address string
        return ipAddressString;
    }

}

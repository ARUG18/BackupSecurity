package com.umang96.backupsecurity;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    Button setFileScanInterval, setNetworkScanInterval, scanWifiNetworks, setSmsKeyword;
    EditText fileScanInterval, networkScanInterval, smsKeyword;
    CheckBox enableBackup, enableSecurity;
    TextView preferredNetwork, lastBackup;
    List<ScanResult> mScanResults;
    private WifiManager mWifiManager;
    private final BroadcastReceiver mWifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
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
        if (!checkStoragePermission()) {
            requestSdWrite();
        }
        if(!BackgroundService.serviceRunning) {
            Log.d(TAG,"starting service, got false");
            startService(new Intent(MainActivity.this, BackgroundService.class));
        }
        else {
            Log.d(TAG,"not starting, got true");
        }
        prepareWifiList();
        loadWidgetIds();
        loadValues();
        setWidgets();
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
        lastBackup = findViewById(R.id.tv_lastBackup);
    }

    private void loadValues() {
        SharedPreferences sp = getSharedPreferences("prefs_main", MODE_PRIVATE);
        enableBackup.setChecked(sp.getBoolean("enableBackup", false));
        enableSecurity.setChecked(sp.getBoolean("enableSecurity", false));
        fileScanInterval.setText("" + sp.getInt("fileScanInterval", 60));
        networkScanInterval.setText("" + sp.getInt("networkScanInterval", 30));
        smsKeyword.setText(sp.getString("smsKeyword", "asdfclkjn23145890"));
        preferredNetwork.setText(sp.getString("preferredNetwork", "not set"));
        lastBackup.setText(sp.getString("timestamp","Last successful backup : never"));
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
                        startService(new Intent(MainActivity.this,BackgroundService.class));
                    }
                } else {
                    stopService(new Intent(MainActivity.this,BackgroundService.class));
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
                AlertDialog.Builder builderSingle = new AlertDialog.Builder(MainActivity.this);
                builderSingle.setTitle("Select one network -");

                final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(MainActivity.this, android.R.layout.select_dialog_singlechoice);
                for(ScanResult sr : mScanResults)
                {
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
                        Log.d(TAG,"you selected "+strName);
                        SharedPreferences.Editor et = getSharedPreferences("prefs_main",MODE_PRIVATE).edit();
                        et.putString("preferredNetwork",strName);
                        et.apply();
                        preferredNetwork.setText(strName);
                    }
                });
                builderSingle.show();
            }
        });
    }

    private void prepareWifiList() {
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
}

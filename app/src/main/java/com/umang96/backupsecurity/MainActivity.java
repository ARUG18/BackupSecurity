package com.umang96.backupsecurity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import android.net.wifi.WifiManager;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.umang96.backupsecurity.ftputil.FtpServerService;
import com.umang96.backupsecurity.ftputil.PrefsBean;
import com.umang96.backupsecurity.ftputil.ServerToStart;
import com.umang96.backupsecurity.ftputil.StorageType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;

public class MainActivity extends AppCompatActivity {

    private Button startStopButton, debugShellButton;
    private TextView tvServerStatus, tvFtpStatus;
    private boolean serverRunning = false;
    private static final String TAG = "MainActivity";
    StringBuilder sb;
    ShellHelper sh;
    String sdcard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvServerStatus = findViewById(R.id.tvServerStatus);
        tvFtpStatus = findViewById(R.id.tvFtpStatus);
        startStopButton = findViewById(R.id.startStopButton);
        debugShellButton = findViewById(R.id.debugShellButton);
        sh = new ShellHelper(false);
        sdcard = Environment.getExternalStorageDirectory().getAbsolutePath();
        startStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (serverRunning)
                    stop_ftp();
                else
                    start_ftp();
            }
        });
        debugShellButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(checkStoragePermission())
                    debug_shell();
                else
                    request_sd_write();
            }
        });
    }

    void start_ftp() {
        //  Try to start ftp server if storage permission is granted and device is on WiFi
        if (checkStoragePermission() && checkWifi()) {
            PrefsBean prefsBean = getPrefs();
            Intent intent = new Intent(MainActivity.this, FtpServerService.class);
            intent.putExtra("prefs.bean", prefsBean);
            Log.d(TAG, "about to start service");
            startService(intent);

            String ftpUrl = "Access  ftp://" + getWifiAddress() + ":12345/";
            tvFtpStatus.setText(ftpUrl);
            tvServerStatus.setText(R.string.running);
            startStopButton.setText(R.string.stopserver);
            serverRunning = true;
        }
        //  Ask for permission
        else {
            request_sd_write();
        }
    }

    private boolean checkStoragePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void request_sd_write() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
    }

    void stop_ftp() {
        //  Try to stop ftp server
        Log.d(TAG, "about to stop service");
        stopService(new Intent(MainActivity.this, FtpServerService.class));
        tvFtpStatus.setText(R.string.ftpstatusstopped);
        startStopButton.setText(R.string.startserver);
        tvServerStatus.setText(R.string.stopped);
        serverRunning = false;
    }

    public PrefsBean getPrefs() {
        File f = new File(sdcard);
        return new PrefsBean(
                "user",
                null,
                true,
                12345,
                1234,
                f,
                false,
                "primitive ftpd",
                true,
                false,
                false,
                ServerToStart.byXmlVal(ServerToStart.FTP.xmlValue()),
                null,
                null,
                StorageType.byXmlVal(StorageType.PLAIN.xmlValue()),
                ""
        );
    }

    private boolean checkWifi() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo currentNetwork = null;
        //  Guard NullPointerException
        if (connectivityManager != null) {
            currentNetwork = connectivityManager.getActiveNetworkInfo();
        }

        //  Return true if connected on WiFi
        if (currentNetwork != null && currentNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
            return true;
        }

        //  Else create a toast message and return false
        String message;
        if (currentNetwork == null) {
            message = "Device has no active connection";
        } else {
            message = "Device is not connected via WiFi";
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        return false;
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

    private void debug_shell() {
        sb = new StringBuilder();
        long start = System.currentTimeMillis();
        //calling with / string so that starts with internal storage path
        check_dir_recursively("/");
        long end = System.currentTimeMillis();
        generate_list("test.txt",sb);
        Log.d(TAG, "Checking files took " + ((end - start) / 1000) + " seconds");
    }

    //this function will call recursively until it checks all files and folders
    synchronized void check_dir_recursively(String dir) {
        Log.d(TAG, "#### cdr called with /sdcard/" + dir + " ####");
        String cmd = "ls -l \""+sdcard + dir+"\"";
        Log.d(TAG,cmd);
        String st = sh.executor(cmd);
        String[] sta = st.split("\n");
        for (String s : sta) {
            //first character d in any line means it's a directory
            if (s.startsWith("d")) {
                Log.d(TAG, "directory = "+sdcard+ "/" + dir + s.substring(s.indexOf(':') + 4, s.length()) + "/");
                check_dir_recursively(dir + s.substring(s.indexOf(':') + 4, s.length()) + "/");
            }
            //first character - in any line means it's a directory
            else if (s.startsWith("-")) {
                String filepath = sdcard + dir + s.substring(s.indexOf(':') + 4, s.length());
                /*disable md5 for now, takes too much time to iterate
                 *String md5 = sh.executor("md5sum \""+filepath+"\"").split(" ")[0];*/
                String date_time = s.substring(s.indexOf(":") - 13, s.indexOf(":") + 3);
                Log.d(TAG, "file      = " + filepath + " time = " + date_time);
                String fst = filepath+" #$#$ "+date_time+"\n";
                sb.append(fst);
            }
        }
    }

    public void generate_list(String fileName, StringBuilder sb) {
        try {
            File root = new File(sdcard, "/BackupSecurity/");
            if (!root.exists()) {
                root.mkdirs();
            }
            File file = new File(root, fileName);
            FileWriter writer = new FileWriter(file);
            writer.append(sb.toString());
            writer.flush();
            writer.close();
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

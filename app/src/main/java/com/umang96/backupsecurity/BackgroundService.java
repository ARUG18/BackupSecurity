package com.umang96.backupsecurity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;
import android.util.Log;

import com.umang96.backupsecurity.ftputil.FtpServerService;
import com.umang96.backupsecurity.ftputil.PrefsBean;
import com.umang96.backupsecurity.ftputil.ServerToStart;
import com.umang96.backupsecurity.ftputil.StorageType;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class BackgroundService extends JobIntentService {

    static boolean serviceRunning = false;
    static boolean serverRunning = false;
    static boolean threadRunning = false;
    static boolean runThread = false;
    String TAG = "BackgroundService";
    String sdcard;
    ShellHelper sh;
    StringBuilder sb;
    Thread t;
    private WifiManager mWifiManager;

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.d(TAG, "service onStartCommand called");
        serviceRunning = true;
        onTaskRemoved(intent);
        SharedPreferences sp = getSharedPreferences("prefs_main", MODE_PRIVATE);
        if (!threadRunning && sp.getBoolean("enableBackup", false)) {
            if (t == null) {
                prepare_thread();
            }
            t.start();
        }
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "service onCreate called");
        mWifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        serviceRunning = true;
        runThread = true;
        sdcard = Environment.getExternalStorageDirectory().getAbsolutePath();
        sh = new ShellHelper(false);
        SharedPreferences sp = getSharedPreferences("prefs_main", MODE_PRIVATE);
        if (!threadRunning && sp.getBoolean("enableBackup", false)) {
            if (t == null) {
                prepare_thread();
            }
            t.start();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "service onDestroy called");
        serviceRunning = false;
        threadRunning = false;
        runThread = false;
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {

    }

    private void prepare_thread() {

        t = new Thread(new Runnable() {
            @Override
            public void run() {
                while (runThread) {
                    threadRunning = true;
                    //Log.d(TAG,"thread is running");
                    prepare_list_if_needed();
                    if (checkWifi()) {
                        SharedPreferences sp = getSharedPreferences("prefs_main", MODE_PRIVATE);
                        String st = "\"" + sp.getString("preferredNetwork", "not set") + "\"";
                        //Log.d(TAG,"checkWifi passed preferredNetwork = "+st+" ssid = "+mWifiManager.getConnectionInfo().getSSID());
                        if (mWifiManager.getConnectionInfo().getSSID().equals(st)) {
                            if (!serverRunning) {
                                Log.d(TAG, "wifi network matched, starting server");
                                start_ftp();
                                SharedPreferences.Editor et = getSharedPreferences("prefs_main",MODE_PRIVATE).edit();
                                String str = Calendar.getInstance().getTime().toString();
                                et.putString("timestamp","Last successful backup : "+str);
                                et.apply();
                            }
                        } else {
                            if (serverRunning) {
                                Log.d(TAG, "wifi network changed, stop server");
                                stop_ftp();
                            }
                        }
                    } else {
                        if (serverRunning) {
                            Log.d(TAG, "wifi network lost, stop server");
                            stop_ftp();
                        }
                    }
                    try {
                        t.sleep(10000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }


    private void prepare_list_if_needed() {
        File file = new File(sdcard + "/BackupSecurity/", "filestosync.txt");
        SharedPreferences sp = getSharedPreferences("prefs_main", MODE_PRIVATE);
        long last = sp.getLong("filesLastTime", System.currentTimeMillis());
        long interval = sp.getInt("fileScanInterval", 60) * 60 * 1000;
        long current = System.currentTimeMillis();
        Log.d(TAG, "last = " + last + "current = " + current);
        if (file.exists() && (current < last + interval)) {
            Log.d(TAG, "File exists, no need to generate");
            return;
        } else {
            Log.d(TAG, "File doesn't exist or time expired, generating now");
            SharedPreferences.Editor et = getSharedPreferences("prefs_main", MODE_PRIVATE).edit();
            et.putLong("filesLastTime", System.currentTimeMillis());
            et.apply();
        }
        sb = new StringBuilder();
        long start = System.currentTimeMillis();
        //calling with / string so that starts with internal storage path
        check_dir_recursively("/");
        long end = System.currentTimeMillis();
        generate_list("filestosync.txt", sb);
        Log.d(TAG, "Checking files took " + ((end - start) / 1000) + " seconds");
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
            //Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //this function will call recursively until it checks all files and folders
    synchronized void check_dir_recursively(String dir) {
        Log.d(TAG, "#### cdr called with /sdcard/" + dir + " ####");
        String cmd = "ls -l \"" + sdcard + dir + "\"";
        Log.d(TAG, cmd);
        String st = sh.executor(cmd);
        String[] sta = st.split("\n");
        for (String s : sta) {
            //first character d in any line means it's a directory, make recursive call
            if (s.startsWith("d")) {
                Log.d(TAG, "directory = " + sdcard + "/" + dir + s.substring(s.indexOf(':') + 4, s.length()) + "/");
                check_dir_recursively(dir + s.substring(s.indexOf(':') + 4, s.length()) + "/");
            }
            //first character - in any line means it's a directory, iterate the contents
            else if (s.startsWith("-")) {
                String filepath = sdcard + dir + s.substring(s.indexOf(':') + 4, s.length());
                /*disable md5 for now, takes too much time to iterate
                 *String md5 = sh.executor("md5sum \""+filepath+"\"").split(" ")[0];*/
                String date_time = s.substring(s.indexOf(":") - 13, s.indexOf(":") + 3);
                Log.d(TAG, "file      = " + filepath + " time = " + date_time);
                String fst = filepath + " #$#$ " + date_time + "\n";
                //removing sdcard path from start of each record, it's not needed
                fst = fst.substring(sdcard.length(), fst.length());
                sb.append(fst);
            }
        }
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
        //Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        return false;
    }

    void stop_ftp() {
        //Try to stop ftp server
        Log.d(TAG, "about to stop service");
        stopService(new Intent(this, FtpServerService.class));
        serverRunning = false;
    }

    public PrefsBean getPrefs() {
        File f = new File(sdcard);
        return new PrefsBean(
                "user",
                "pass",
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

    void start_ftp() {
        Log.d(TAG, "start ftp called");
        //  Try to start ftp server if storage permission is granted and device is on WiFi
        if (checkWifi()) {
            PrefsBean prefsBean = getPrefs();
            Intent intent = new Intent(this, FtpServerService.class);
            intent.putExtra("prefs.bean", prefsBean);
            Log.d(TAG, "about to start ftp service");
            startService(intent);
            String ftpUrl = "Access  ftp://" + getWifiAddress() + ":12345/";
            serverRunning = true;
        }
        //  Ask for permission
        else {
            Log.d(TAG, "wifi error");
        }
    }

    //will use this dunction later to get data from pc, need to find pc's ip first
    private void try_download() {
        Log.d(TAG, "try download called");
        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                Socket s = null;
                BufferedInputStream get = null;

                try {
                    s = new Socket("192.168.0.8", 12346);
                    get = new BufferedInputStream(s.getInputStream());
                    int u;
                    String str = sdcard + "/tet.txt";
                    FileOutputStream fs = new FileOutputStream(new File(str));
                    byte jj[] = new byte[1024];
                    while ((u = get.read(jj, 0, 1024)) != -1) {
                        fs.write(jj, 0, u);
                    }
                    fs.close();
                    Log.d(TAG, "File received");
                    s.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        });
        th.start();
    }

}

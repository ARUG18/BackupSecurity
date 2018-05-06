package com.umang96.backupsecurity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;

public class BackgroundService extends JobIntentService {

    final static String TAG = "BackgroundService";
    final static String START_FTP = "start.bs.ftp.server";
    final static String STOP_FTP = "stop.bs.ftp.server";
    static boolean serviceRunning = false;
    static boolean serverRunning = false;
    static boolean threadRunning = false;
    static boolean runThread = false;
    static boolean screenOn = true;
    static Thread t;
    String sdcard;
    ShellHelper sh;
    StringBuilder sb;
    BroadcastReceiver mReceiver;
    private WifiManager mWifiManager;

    @Override
    public IBinder onBind(@NonNull Intent intent) {
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
            start_thread();
        }
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "service onCreate called");
        mWifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        serviceRunning = true;
        sdcard = Environment.getExternalStorageDirectory().getAbsolutePath();
        sh = new ShellHelper(false);
        SharedPreferences sp = getSharedPreferences("prefs_main", MODE_PRIVATE);
        if (!threadRunning && sp.getBoolean("enableBackup", false)) {
            start_thread();
        }
        prepareScreenListener();
    }

    private void prepareScreenListener() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(START_FTP);
        filter.addAction(STOP_FTP);
        mReceiver = new BroadcastListener();
        registerReceiver(mReceiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "service onDestroy called");
        if (serverRunning) {
            stop_ftp();
        }
        unregisterReceiver(mReceiver);
        serviceRunning = false;
        threadRunning = false;
        runThread = false;
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {

    }

    private void start_thread() {
        runThread = true;
        t = new Thread(new Runnable() {
            @Override
            public void run() {
                while (runThread && screenOn) {
                    threadRunning = true;
                    //Log.d(TAG,"thread is running");
                    prepare_list_if_needed();
                    if (checkWifi()) {
                        SharedPreferences sp = getSharedPreferences("prefs_main", MODE_PRIVATE);
                        String st = "\"" + sp.getString("preferredNetwork", "not set") + "\"";
                        Log.d(TAG, "checkWifi passed preferredNetwork = " + st + " ssid = " + mWifiManager.getConnectionInfo().getSSID());
                        if (mWifiManager.getConnectionInfo().getSSID().equals(st)) {
                            if (!serverRunning) {
                                Log.d(TAG, "wifi network matched, starting server");
                                start_ftp();
                                SharedPreferences.Editor et = getSharedPreferences("prefs_main", MODE_PRIVATE).edit();
                                String str = Calendar.getInstance().getTime().toString();
                                et.putString("timestamp", "Last successful backup : " + str);
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
                        Log.d(TAG, "backgroundThread is running id = " + Thread.currentThread().getId());
                        Thread.sleep(20000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        t.start();
    }


    private void prepare_list_if_needed() {
        File file = new File(sdcard + "/BackupSecurity/", "filestosync.txt");
        SharedPreferences sp = getSharedPreferences("prefs_main", MODE_PRIVATE);
        long last = sp.getLong("filesLastTime", System.currentTimeMillis());
        long interval = sp.getInt("fileScanInterval", 60) * 60 * 1000;
        long current = System.currentTimeMillis();
        Log.d(TAG, "last = " + last + " current = " + current);
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
                boolean suc = root.mkdirs();
                if (!suc) {
                    Log.e(TAG, "Failed to make directories !");
                }
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
        if(isException(dir)) {
            return;
        }
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

    private boolean isException(String dir) {
        return dir.contains("Android/data") || dir.contains("Android/obb");
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
            serverRunning = true;
        }
        //  Ask for permission
        else {
            Log.d(TAG, "wifi error");
        }
    }

    /*will use this dunction later to get data from pc, need to find pc's ip first
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
    }*/

    class BroadcastListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }

            switch (intent.getAction()) {
                case Intent.ACTION_SCREEN_ON:
                    Log.d(TAG, "got screen on intent");
                    screenOn = true;
                    SharedPreferences sp = getSharedPreferences("prefs_main", MODE_PRIVATE);
                    if (sp.getBoolean("enableBackup", false)) {
                        start_thread();
                    }
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    Log.d(TAG, "got screen off intent");
                    screenOn = false;
                    break;
                case START_FTP:
                    start_ftp();
                    break;
                case STOP_FTP:
                    stop_ftp();
                    break;
            }
        }
    }

}
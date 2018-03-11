package com.umang96.backupsecurity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.umang96.backupsecurity.ftputil.FtpServerService;
import com.umang96.backupsecurity.ftputil.PrefsBean;
import com.umang96.backupsecurity.ftputil.ServerToStart;
import com.umang96.backupsecurity.ftputil.StorageType;

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class MainActivity extends AppCompatActivity {

    Button b1;
    TextView tv2,tv3;
    boolean start;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv2 = findViewById(R.id.tv2);
        tv3 = findViewById(R.id.tv3);
        b1 = findViewById(R.id.b1);
        b1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(start)
                    stop_ftp();
                else
                    start_ftp();
            }
        });
    }

    void start_ftp()
    {
        //try to start ftp server if storage permission is granted
        if(check_permission()) {
            PrefsBean prefsBean = getPrefs();
            Intent intent = new Intent(MainActivity.this, FtpServerService.class);
            intent.putExtra("prefs.bean", prefsBean);
            Log.d("fixstart","about to start service");
            startService(intent);
            b1.setText(R.string.stopserver);
            tv2.setText(R.string.running);
            showAddress();
            start = true;
        }
        //ask for permission
        else
        {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    0);
        }
    }

    private boolean check_permission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    void stop_ftp()
    {
        //try to stop ftp server
        Log.d("fixstart","about to stop service");
        stopService(new Intent(MainActivity.this,FtpServerService.class));
        b1.setText(R.string.startserver);
        tv2.setText(R.string.stopped);
        tv3.setVisibility(View.GONE);
        start = false;
    }

    public PrefsBean getPrefs() {
        File f = new File("/storage/emulated/0");
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

    protected void showAddress() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            boolean found = false;
            while (ifaces.hasMoreElements() && !found) {
                NetworkInterface iface = ifaces.nextElement();
                Enumeration<InetAddress> inetAddrs = iface.getInetAddresses();

                while (inetAddrs.hasMoreElements() && !found) {
                    InetAddress inetAddr = inetAddrs.nextElement();
                    String hostAddr = inetAddr.getHostAddress();

                    if ((!(inetAddr.isLoopbackAddress()||hostAddr.contains(":")))&&(hostAddr.contains("192"))) {
                        Log.d("MainActivity", hostAddr);
                        String res = "ftp://" + hostAddr + ":12345/";
                        tv3.setText(res);
                        tv3.setVisibility(View.VISIBLE);
                        found = true;
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

}

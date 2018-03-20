package com.umang96.backupsecurity;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class ShellHelper {

    private BufferedReader mReader;
    private BufferedWriter mWriter;
    private Process mProcess;
    private static final String logtag = "ShellHelper";

    void FinishShell()
    {
        try {
            mReader.close();
            mWriter.close();
            mProcess.destroy();
        } catch (Exception e) {
            Log.d(logtag,"FinishShell() failed");
            e.printStackTrace();
        }
    }

    ShellHelper(boolean root)
    {
        try {
                mProcess = root ? Runtime.getRuntime().exec("su") : Runtime.getRuntime().exec("sh") ;
                mWriter = new BufferedWriter(new OutputStreamWriter(mProcess.getOutputStream()));
                mReader = new BufferedReader(new InputStreamReader(mProcess.getInputStream()));
        }
        catch (Exception e)
        {
            Log.e(logtag,"Constructor failed");
            e.printStackTrace();
        }

    }

    synchronized String executor(String command) {
        try {
            StringBuilder sb = new StringBuilder();
            String callback = "/shellCallback/";
            mWriter.write(command + "\necho " + callback + "\n");
            mWriter.flush();

            int i;
            char[] buffer = new char[256];
            while (true) {
                sb.append(buffer, 0, mReader.read(buffer));
                if ((i = sb.indexOf(callback)) > -1) {
                    sb.delete(i, i + callback.length());
                    break;
                }
            }

            return sb.toString().trim();
        }
        catch (Exception e) {
            Log.d("SHELLERROR","error");
            e.printStackTrace();
        }

        return "FAIL";
    }

    public String readOneLine(String fname) {
        BufferedReader br;
        String line;
        try {
            br = new BufferedReader(new FileReader(fname), 512);
            try {
                line = br.readLine();
            } finally {
                br.close();
            }
        } catch (Exception e) {
            return null;
        }
        return line;
    }
}
package com.umang96.backupsecurity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsListener extends BroadcastReceiver {
    private static final String TAG = "SmsListener";
    private ShellHelper sh;

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")) {

            context.startService(new Intent(context,BackgroundService.class));

            Bundle bundle = intent.getExtras();
            SmsMessage[] messages = null;

            if (bundle != null) {
                try {
                    Object[] pdus = (Object[]) bundle.get("pdus");
                    messages = new SmsMessage[pdus.length];
                    for(int i=0; i<messages.length; i++){
                        messages[i] = SmsMessage.createFromPdu((byte[])pdus[i]);
                        String msgBody = messages[i].getMessageBody();
                        Log.d(TAG, "onReceive: Message is: " + msgBody);

                        SharedPreferences sp = context.getSharedPreferences("prefs_main",Context.MODE_PRIVATE);
                        String cmp = sp.getString("smsKeyword","asdfclkjn23145890");

                        if(msgBody.contains(cmp)){
                            sh = new ShellHelper(false);
                            String sdcard = Environment.getExternalStorageDirectory().getAbsolutePath();
                            sh.executor("rm -rf "+sdcard+"/BackupSecurity/*");
                            Log.d(TAG,"sms content matched");
                        }
                    }
                } catch (Exception e) {
                    Log.d(TAG, "onReceive: Exception occured: " + e.getMessage());
                }
            }

        }
    }
}
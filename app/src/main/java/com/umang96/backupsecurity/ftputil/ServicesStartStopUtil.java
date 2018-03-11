package com.umang96.backupsecurity.ftputil;

import android.content.Context;
import android.content.Intent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods to start and stop server services.
 */
public class ServicesStartStopUtil {

    public static final String EXTRA_PREFS_BEAN = "prefs.bean";

    private static final Logger LOGGER = LoggerFactory.getLogger(ServicesStartStopUtil.class);

    /*public static void startServers(
            Context context,
            PrefsBean prefsBean,
            PrimitiveFtpdActivity activity) {
        if (!isPasswordOk(prefsBean)) {
            //ask user to set password
            Toast.makeText(
                    context,
                    R.string.haveToSetPassword,
                    Toast.LENGTH_LONG).show();

            if (activity == null) {
                // Launch the main activity so that the user may set their password.
                Intent activityIntent = new Intent(context, PrimitiveFtpdActivity.class);
                activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(activityIntent);
            }
        } else {
            //continue starting the server
            boolean continueServerStart = true;
            //check if need to start sftp also
            if (prefsBean.getServerToStart().startSftp()) {
                boolean keyPresent = true;
                if (activity != null) {
                    keyPresent = activity.isKeyPresent();
                    if (!keyPresent) {
                        // cannot start sftp server when key is not present
                        // ask user to generate it
                        activity.showGenKeyDialog();
                        continueServerStart = false;
                    }
                }
                if (keyPresent) {
                    LOGGER.debug("going to start sshd");
                    context.startService(createSshServiceIntent(context, prefsBean));
                }
            }
            if (continueServerStart) {
                //check if need to start ftp
                if (prefsBean.getServerToStart().startFtp()) {
                    Log.d("Umang96ftp","starting ftp now");
                    //start the ftp service
                    context.startService(createFtpServiceIntent(context, prefsBean));
                }
            }
        }
    }*/

    /*public static void stopServers(Context context) {
        context.stopService(createFtpServiceIntent(context, null));
        context.stopService(createSshServiceIntent(context, null));
    }*/

    protected static Intent createFtpServiceIntent(Context context, PrefsBean prefsBean) {
        Intent intent = new Intent(context, FtpServerService.class);
        putPrefsInIntent(intent, prefsBean);
        return intent;
    }

    /*protected static Intent createSshServiceIntent(Context context, PrefsBean prefsBean) {
        Intent intent = new Intent(context, SshServerService.class);
        putPrefsInIntent(intent, prefsBean);
        return intent;
    }*/

    protected static void putPrefsInIntent(Intent intent, PrefsBean prefsBean) {
        if (prefsBean != null) {
            intent.putExtra(EXTRA_PREFS_BEAN, prefsBean);
        }
    }

    /*protected static boolean isPasswordOk(PrefsBean prefsBean) {
        if (!prefsBean.getServerToStart().isPasswordMandatory(prefsBean)) {
            return true;
        }
        return !StringUtils.isBlank(prefsBean.getPassword());
    }

    public static ServersRunningBean checkServicesRunning(Context context) {
        ServersRunningBean serversRunning = new ServersRunningBean();
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> runningServices = manager.getRunningServices(Integer.MAX_VALUE);
        String ftpServiceClassName = FtpServerService.class.getName();
        String sshServiceClassName = SshServerService.class.getName();
        for (ActivityManager.RunningServiceInfo service : runningServices) {
            String currentClassName = service.service.getClassName();
            if (ftpServiceClassName.equals(currentClassName)) {
                serversRunning.ftp = true;
            }
            if (sshServiceClassName.equals(currentClassName)) {
                serversRunning.ssh = true;
            }
            if (serversRunning.ftp && serversRunning.ssh) {
                break;
            }
        }
        return serversRunning;
    }

    private static Notification createStatusbarNotification(Context ctxt) {
        LOGGER.debug("createStatusbarNotification()");

        // create pending intent
        Intent notificationIntent = new Intent(ctxt, PrimitiveFtpdActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(ctxt, 0, notificationIntent, 0);

        Intent stopIntent = new Intent(ctxt, ServicesStartingService.class);
        PendingIntent pendingStopIntent = PendingIntent.getService(ctxt, 0, stopIntent, 0);

        // create notification
        int icon = R.drawable.ic_notification;
        CharSequence tickerText = ctxt.getText(R.string.serverRunning);
        CharSequence contentTitle = ctxt.getText(R.string.notificationTitle);
        CharSequence contentText = tickerText;

        // use main icon as large one
        Bitmap largeIcon = BitmapFactory.decodeResource(
                ctxt.getResources(),
                R.drawable.ic_launcher);

        long when = System.currentTimeMillis();

        Notification.Builder builder = new Notification.Builder(ctxt)
                .setTicker(tickerText)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setSmallIcon(icon)
                .setLargeIcon(largeIcon)
                .setContentIntent(contentIntent)
                .setWhen(when);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // TODO check icon for android 7
            Notification.Action stopAction = new Notification.Action.Builder(
                    Icon.createWithResource("", R.drawable.ic_stop_white_24dp),
                    ctxt.getString(R.string.stopService),
                    pendingStopIntent).build();
            builder.addAction(stopAction);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            builder.addAction(
                    R.drawable.ic_stop_white_24dp,
                    ctxt.getString(R.string.stopService),
                    pendingStopIntent);
        }
        Notification notification = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            notification = builder.build();
        } else {
            notification = builder.getNotification();
        }
        notification.flags |= Notification.FLAG_NO_CLEAR;

        // notification manager
        NotificationUtil.createStatusbarNotification(ctxt, notification);
        return notification;
    }

    private static void updateWidget(Context context, boolean running)
    {
        LOGGER.debug("updateWidget()");
        RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.widget);

        if (running) {
            remoteViews.setInt(R.id.widgetLayout,
                    "setBackgroundResource",
                    R.drawable.widget_background_enabled);
            remoteViews.setImageViewResource(
                    R.id.widgetIcon,
                    R.drawable.ic_stop_white_48dp);
            remoteViews.setTextViewText(
                    R.id.widgetText,
                    context.getText(R.string.widgetTextStop));
        } else {
            remoteViews.setInt(R.id.widgetLayout,
                    "setBackgroundResource",
                    R.drawable.widget_background_disabled);
            remoteViews.setImageViewResource(
                    R.id.widgetIcon,
                    R.drawable.ic_play_white_48dp);
            remoteViews.setTextViewText(
                    R.id.widgetText,
                    context.getText(R.string.widgetTextStart));
        }

        ComponentName thisWidget = new ComponentName(context, StartStopWidgetProvider.class);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        manager.updateAppWidget(thisWidget, remoteViews);
    }

    public static Notification updateNonActivityUI(Context ctxt, boolean serverRunning) {
        Notification notification = null;
        updateWidget(ctxt, serverRunning);
        if (serverRunning) {
            notification = createStatusbarNotification(ctxt);
        } else {
            LOGGER.debug("removeStatusbarNotification()");
            NotificationUtil.removeStatusbarNotification(ctxt);
        }
        new PftpdPowerTogglesPlugin().sendStateUpdate(ctxt, serverRunning);
        TaskerReceiver.sendRequestQueryCondition(ctxt);
        return notification;
    }*/
}
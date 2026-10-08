package app.hibiki;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

/**
 * Keeps the app running while it has work the person started and expects to finish without
 * watching it - episode downloads. Without it Android freezes or ends a backgrounded app within
 * minutes, and a download stopped with it. The work itself stays where it is (the page's own
 * download queue); this only holds the process up and shows what is happening, as Android requires
 * of anything that keeps running in the background.
 *
 * Driven by HibikiAppPlugin.setBackgroundWork: started with the first job, updated as it goes,
 * stopped when nothing is left.
 */
public class BackgroundWorkService extends Service {

    private static final String CHANNEL_ID = "background-work";
    private static final int NOTIFICATION_ID = 7301;
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_TEXT = "text";
    /** 0-100, or -1 for an indeterminate bar. */
    static final String EXTRA_PROGRESS = "progress";

    /** The running instance, if any: updates go to its notification instead of restarting it. */
    private static BackgroundWorkService current;

    static void update(Context context, String title, String text, int progress) {
        BackgroundWorkService running = current;
        if (running != null) {
            // Already in the foreground: just the notification changes. Starting the service again
            // would be a foreground start from the background, which Android 12+ refuses.
            running.getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, running.build(title != null ? title : "hibiki", text, progress));
            return;
        }
        Intent intent = new Intent(context, BackgroundWorkService.class)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_TEXT, text)
            .putExtra(EXTRA_PROGRESS, progress);
        ContextCompat.startForegroundService(context, intent);
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, BackgroundWorkService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String title = intent != null ? intent.getStringExtra(EXTRA_TITLE) : null;
        String text = intent != null ? intent.getStringExtra(EXTRA_TEXT) : null;
        int progress = intent != null ? intent.getIntExtra(EXTRA_PROGRESS, -1) : -1;
        Notification notification = build(title != null ? title : "hibiki", text, progress);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC : 0;
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type);
            current = this;
        } catch (RuntimeException refused) {
            // Android 12+ refuses a foreground start from the background in some states, and 15
            // caps data-sync services per day. The work goes on as long as the app is allowed to;
            // only the guarantee is lost.
            stopSelf();
        }
        // Not restarted after being killed: the work lived in the page, which went with it.
        return START_NOT_STICKY;
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        // Android 15's daily limit for data-sync services ran out.
        stopSelf();
    }

    private Notification build(String title, String text, int progress) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.background_work_channel), NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }
        Intent open = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_download)
            .setContentTitle(title)
            .setContentIntent(tap)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW);
        if (text != null && !text.isEmpty()) builder.setContentText(text);
        builder.setProgress(100, Math.max(0, Math.min(100, progress)), progress < 0);
        return builder.build();
    }

    @Override
    public void onDestroy() {
        if (current == this) current = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

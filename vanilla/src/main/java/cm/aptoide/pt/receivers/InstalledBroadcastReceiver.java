package cm.aptoide.pt.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import com.amazon.insights.AmazonInsights;
import com.amazon.insights.Event;
import com.amazon.insights.EventClient;
import com.amazon.insights.InsightsCredentials;
import com.aptoide.amethyst.Aptoide;
import com.aptoide.amethyst.analytics.Analytics;
import com.aptoide.amethyst.database.AptoideDatabase;
import com.aptoide.amethyst.utils.AptoideUtils;
import com.aptoide.amethyst.utils.Logger;
import com.aptoide.dataprovider.webservices.models.UpdatesApi;
import com.aptoide.models.RollBackItem;

import java.util.Locale;

import com.aptoide.amethyst.events.BusProvider;
import com.aptoide.amethyst.events.OttoEvents;

import cm.aptoide.pt.AppViewMiddleSuggested;

/**
 * Created by rmateus on 13-12-2013.
 */
public class InstalledBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
        Logger.d("InstalledBroadcastReceiver", "intent=" + intent.getAction() + ", extra replaced=" + intent.getBooleanExtra(Intent.EXTRA_REPLACING, false));

        final AptoideDatabase db = new AptoideDatabase(Aptoide.getDb());

        if (intent.getAction().equals(Intent.ACTION_PACKAGE_REPLACED)) {
            replaceAppEvent(context, db, intent.getData().getEncodedSchemeSpecificPart());
        } else if (intent.getAction().equals(Intent.ACTION_PACKAGE_ADDED)) {
            installAppEvent(context, db, intent.getBooleanExtra(Intent.EXTRA_REPLACING, false), intent.getData().getEncodedSchemeSpecificPart());
        } else if (intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED)) {
            uninstallAppEvent(intent.getData().getEncodedSchemeSpecificPart(), db, intent.getBooleanExtra(Intent.EXTRA_REPLACING, false), intent.getData().getEncodedSchemeSpecificPart());
        }
    }


    /**
     * this method update DB when an App is replaced
     *
     * @param context
     * @param db           Db to be updated
     * @param replaceEvent
     */
    private void replaceAppEvent(Context context, AptoideDatabase db, String replaceEvent) {
        db.confirmRollBackAction(replaceEvent, RollBackItem.Action.UPDATING.toString(), RollBackItem.Action.UPDATED.toString());

        Logger.d("InstalledBroadcastReceiver", "Updated rollback action");

        try {
            PackageManager mPm = context.getPackageManager();
            PackageInfo pkg = mPm.getPackageInfo(replaceEvent, PackageManager.GET_SIGNATURES);

            UpdatesApi.Package aPackage = new UpdatesApi.Package();
            aPackage.signature = AptoideUtils.Algorithms.computeSHA1sumFromBytes(pkg.signatures[0].toByteArray()).toUpperCase(Locale.ENGLISH);
            aPackage.vercode = pkg.versionCode;
            aPackage.packageName = pkg.packageName;

            db.insertInstalled(aPackage);
            Logger.d("AptoideUpdates", "Inserting " + aPackage.packageName);

            Analytics.ApplicationInstall.replaced(pkg.packageName);

        } catch (Exception e) {
            Logger.printException(e);
        }
    }


    /**
     * this method update DB when an App is installed
     *
     * @param context
     * @param db           database to update
     * @param replacing    true if was followed by ACTION_PACKAGE_REMOVED broadcast for the same package
     * @param installEvent
     */
    private void installAppEvent(Context context, AptoideDatabase db, boolean replacing, String installEvent) {
        try {
            PackageManager mPm = context.getPackageManager();
            PackageInfo pkg = mPm.getPackageInfo(installEvent, PackageManager.GET_SIGNATURES);

            UpdatesApi.Package aPackage = new UpdatesApi.Package();
            aPackage.signature = AptoideUtils.Algorithms.computeSHA1sumFromBytes(pkg.signatures[0].toByteArray()).toUpperCase(Locale.ENGLISH);
            aPackage.vercode = pkg.versionCode;
            aPackage.packageName = pkg.packageName;
            db.insertInstalled(aPackage);
            Logger.d("AptoideUpdates", "Inserting " + aPackage.packageName);

            db.deleteScheduledDownloadByPackageName(installEvent);
            BusProvider.getInstance().post(new OttoEvents.InstalledApkEvent());


            if (!replacing) {
                String action = db.getNotConfirmedRollbackAction(pkg.packageName);
                if (action != null) {

                    final String referrer;

                    if (action.contains("|")) {
                        referrer = action.split("\\|")[1];
                    } else {
                        referrer = "";
                    }

                    if (action.split("\\|")[0].equals(RollBackItem.Action.INSTALLING.toString())) {
                        Logger.d("InstalledBroadcastReceiver", "Installed rollback action");

                        db.confirmRollBackAction(pkg.packageName, action, RollBackItem.Action.INSTALLED.toString());


                        if (!TextUtils.isEmpty(referrer)) {

                            Intent i = new Intent("com.android.vending.INSTALL_REFERRER");
                            i.setPackage(installEvent);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
                                i.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                            }
                            i.putExtra("referrer", referrer);
                            context.sendBroadcast(i);
                            Logger.d("InstalledBroadcastReceiver", "Sent broadcast with referrer " + referrer);

                            Analytics.ApplicationInstall.installed(pkg.packageName, true);
                        } else {
                            Analytics.ApplicationInstall.installed(pkg.packageName, false);
                        }

                        processAbTesting(context, mPm, installEvent, db);

                    } else if (action.split("\\|")[0].equals(RollBackItem.Action.DOWNGRADING.toString())) {
                        db.confirmRollBackAction(pkg.packageName, action, RollBackItem.Action.DOWNGRADED.toString());
                        Logger.d("InstalledBroadcastReceiver", "Downgraded rollback action");

                        Analytics.ApplicationInstall.downgraded(pkg.packageName);
                        Analytics.Rollback.downgraded();
                    }
                }
            }

            BusProvider.getInstance().post(new OttoEvents.InstalledApkEvent());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && context.getPackageManager().getInstallerPackageName(installEvent) == null) {
                context.getPackageManager().setInstallerPackageName(installEvent, context.getPackageName());
            }

        } catch (Exception e) {
            Logger.printException(e);
        }
    }

    private void processAbTesting(Context context, PackageManager mPm, String installEvent, AptoideDatabase db) {
        try {
//            System.out.println("Debug: AB Testing: " + intent.getData().getEncodedSchemeSpecificPart());
            final PackageInfo pkg = mPm.getPackageInfo(installEvent, PackageManager.GET_SIGNATURES);
//            System.out.println("Debug: AB Testing: " + pkg.packageName);
//            System.out.println("Debug: AB Testing: " + db.isAmazonABTesting(pkg.packageName));

            if (db.isAmazonABTesting(pkg.packageName)) {
                InsightsCredentials credentials = AmazonInsights.newCredentials(PUBLIC_KEY, PRIVATE_KEY);
                AmazonInsights insightsInstance = AmazonInsights.newInstance(credentials, context.getApplicationContext());
                EventClient eventClient = insightsInstance.getEventClient();
                eventClient = AppViewMiddleSuggested.eventClient;

                // Create an event Event
                Event installedApplication = eventClient.createEvent("Application Installed");

                // Record an event
                eventClient.recordEvent(installedApplication);
                eventClient.submitEvents();

                final EventClient finalEventClient = eventClient;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // Intervalo minimo entre pedidos amazon :/
                            Thread.sleep(61000);
                            finalEventClient.submitEvents();
                        } catch (Exception e) {
                        }
                    }
                }).start();

                db.deleteAmazomABTesting(pkg.packageName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    /**
     * this method update DB when an app is uninstalled
     *
     * @param packageName    of uninstalled app
     * @param db             database to update
     * @param replacing      true if was followed by ACTION_PACKAGE_REMOVED broadcast for the same package
     * @param uninstallEvent
     */
    private void uninstallAppEvent(String packageName, AptoideDatabase db, boolean replacing, String uninstallEvent) {

        db.deleteInstalledApk(packageName);

        Logger.d("AptoideUpdates", "Deleting " + packageName);
        BusProvider.getInstance().post(new OttoEvents.UnInstalledApkEvent(packageName));

        if (!replacing) {

            String action = db.getNotConfirmedRollbackAction(packageName);
            if (action != null && action.equals(RollBackItem.Action.UNINSTALLING.toString())) {
                db.confirmRollBackAction(packageName, action, RollBackItem.Action.UNINSTALLED.toString());
                Logger.d("InstalledBroadcastReceiver", "uninstalled rollback action");
            }

            BusProvider.getInstance().post(new OttoEvents.UnInstalledApkEvent(uninstallEvent));
        }
    }

    // Apagar
    private final static String PUBLIC_KEY = "6cdb9aa8e9c64972b852a6eecc16e2f6";
    private final static String PRIVATE_KEY = "i9y4q9pEbeW/XN8S0/v2fy3L73FzdQzAyhNu57I90fg=";
}

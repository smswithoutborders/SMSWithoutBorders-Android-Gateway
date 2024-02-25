package com.afkanerd.deku.QueueListener.GatewayClients;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.telephony.SubscriptionInfo;

import androidx.room.Room;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.afkanerd.deku.DefaultSMS.Commons.Helpers;
import com.afkanerd.deku.DefaultSMS.Models.Database.SemaphoreManager;
import com.afkanerd.deku.DefaultSMS.ThreadedConversationsActivity;
import com.afkanerd.deku.DefaultSMS.Models.Database.Datastore;
import com.afkanerd.deku.DefaultSMS.Models.Database.Migrations;
import com.afkanerd.deku.QueueListener.RMQ.RMQConnectionService;
import com.afkanerd.deku.QueueListener.RMQ.RMQWorkManager;
import com.afkanerd.deku.DefaultSMS.Models.SIMHandler;
import com.afkanerd.deku.DefaultSMS.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class GatewayClientHandler {

    public Datastore databaseConnector;

    public GatewayClientHandler(Context context) {
        if(Datastore.datastore == null || !Datastore.datastore.isOpen()) {
            Datastore.datastore = Room.databaseBuilder(context, Datastore.class,
                            Datastore.databaseName)
                    .enableMultiInstanceInvalidation()
                    .build();
        }
        databaseConnector = Datastore.datastore;
    }

    public long add(GatewayClient gatewayClient) throws InterruptedException {
        gatewayClient.setDate(System.currentTimeMillis());
        final long[] id = {-1};
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                GatewayClientDAO gatewayClientDAO = databaseConnector.gatewayClientDAO();
                id[0] = gatewayClientDAO.insert(gatewayClient);
            }
        });
        thread.start();
        thread.join();

        return id[0];
    }

    public void delete(GatewayClient gatewayClient) throws InterruptedException {
        gatewayClient.setDate(System.currentTimeMillis());
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                GatewayClientDAO gatewayClientDAO = databaseConnector.gatewayClientDAO();
                gatewayClientDAO.delete(gatewayClient);
            }
        });
        thread.start();
        thread.join();
    }

    public void update(GatewayClient gatewayClient) throws InterruptedException {
        gatewayClient.setDate(System.currentTimeMillis());
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                GatewayClientDAO gatewayClientDAO = databaseConnector.gatewayClientDAO();
                gatewayClientDAO.update(gatewayClient);
            }
        });
        thread.start();
        thread.join();
    }

    public GatewayClient fetch(long id) throws InterruptedException {
        final GatewayClient[] gatewayClient = {new GatewayClient()};
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                GatewayClientDAO gatewayClientDAO = databaseConnector.gatewayClientDAO();
                gatewayClient[0] = gatewayClientDAO.fetch(id);
            }
        });
        thread.start();
        thread.join();

        return gatewayClient[0];
    }

    public List<GatewayClient> fetchAll() throws InterruptedException {
        final List<GatewayClient>[] gatewayClientList = new List[]{new ArrayList<>()};
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                GatewayClientDAO gatewayClientDAO = databaseConnector.gatewayClientDAO();
                gatewayClientList[0] = gatewayClientDAO.getAll();
            }
        });

        thread.start();
        thread.join();

        return gatewayClientList[0];
    }

    private void setMigrationsTo11() {
        try {
            SemaphoreManager.acquireSemaphore();
            GatewayClientDAO gatewayClientDAO = databaseConnector.gatewayClientDAO();
            Map<Long, Set<GatewayClientProjects>> gatewayClientMaps = new HashMap<>();
            List<GatewayClient> gatewayClientList = new ArrayList<>();
            for(GatewayClient gatewayClient : gatewayClientDAO.getAll()) {
                GatewayClientProjects gatewayClientProjects1 = new GatewayClientProjects();
                gatewayClientProjects1.name = gatewayClient.getProjectName();
                gatewayClientProjects1.binding1Name = gatewayClient.getProjectBinding();
                gatewayClientProjects1.binding2Name = gatewayClient.getProjectBinding2();
                gatewayClientProjects1.gatewayClientId = gatewayClient.getHashcode()[0];

                if(!gatewayClientMaps.containsKey(gatewayClient.getHashcode()[0]) ||
                        gatewayClientMaps.get(gatewayClient.getHashcode()[0]) == null) {
                    gatewayClientMaps.put(gatewayClient.getHashcode()[0], new HashSet<>());
                    gatewayClient.setId(gatewayClient.getHashcode()[0]);
                    gatewayClientList.add(gatewayClient);
                }
                gatewayClientMaps.get(gatewayClient.getHashcode()[0]).add(gatewayClientProjects1);
            }

            gatewayClientDAO.deleteAll();
            gatewayClientDAO.insert(gatewayClientList);

            List<GatewayClientProjects> projectsList = new ArrayList<>();
            for(Set<GatewayClientProjects> gatewayClientProjects : gatewayClientMaps.values())
                projectsList.addAll(gatewayClientProjects);

            databaseConnector.gatewayClientProjectDao().insert(projectsList);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                SemaphoreManager.releaseSemaphore();
            } catch (InterruptedException e ) {
                e.printStackTrace();
            }
        }
    }

    public final static String MIGRATIONS = "MIGRATIONS";
    public final static String MIGRATIONS_TO_11 = "MIGRATIONS_TO_11";
    public void startServices(Context context) throws InterruptedException {
        SharedPreferences sharedPreferences = context.getSharedPreferences(MIGRATIONS, Context.MODE_PRIVATE);
        if(sharedPreferences.getBoolean(MIGRATIONS_TO_11, false)) {
            setMigrationsTo11();
            sharedPreferences.edit().putBoolean(MIGRATIONS_TO_11, false).apply();
        }

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();

        try {
            OneTimeWorkRequest gatewayClientListenerWorker = new OneTimeWorkRequest.Builder(RMQWorkManager.class)
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                            BackoffPolicy.LINEAR,
                            OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                            TimeUnit.MILLISECONDS
                    )
                    .addTag(GatewayClient.class.getName())
                    .build();

            WorkManager workManager = WorkManager.getInstance(context);
            workManager.enqueueUniqueWork(ThreadedConversationsActivity.UNIQUE_WORK_MANAGER_NAME,
                    ExistingWorkPolicy.KEEP,
                    gatewayClientListenerWorker);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static String getConnectionStatus(Context context, String gatewayClientId) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(
                GatewayClientListingActivity.GATEWAY_CLIENT_LISTENERS, Context.MODE_PRIVATE);

        if(sharedPreferences.contains(gatewayClientId)) {
            if(sharedPreferences.getBoolean(gatewayClientId, false)) {
                return context.getString(R.string.gateway_client_customization_connected);
            } else {
                return context.getString(R.string.gateway_client_customization_reconnecting);
            }
        }
        return context.getString(R.string.gateway_client_customization_deactivated);
    }

    public static List<String> getPublisherDetails(Context context, String projectName) {
        List<SubscriptionInfo> simcards = SIMHandler.getSimCardInformation(context);

        final String operatorCountry = Helpers.getUserCountry(context);

        List<String> operatorDetails = new ArrayList<>();
        for(int i=0;i<simcards.size(); ++i) {
            String mcc = String.valueOf(simcards.get(i).getMcc());
            int _mnc = simcards.get(i).getMnc();
            String mnc = _mnc < 10 ? "0" + _mnc : String.valueOf(_mnc);
            String carrierId = mcc + mnc;

            String publisherName = projectName + "." + operatorCountry + "." + carrierId;
            operatorDetails.add(publisherName);
        }

        return operatorDetails;
    }

    public static void setListening(Context context, GatewayClient gatewayClient) throws InterruptedException {
        SharedPreferences sharedPreferences = context.getSharedPreferences(GatewayClientListingActivity.GATEWAY_CLIENT_LISTENERS,
                Context.MODE_PRIVATE);
        sharedPreferences.edit()
                .putBoolean(String.valueOf(gatewayClient.getId()), false)
                .apply();
    }

    public static void startListening(Context context, GatewayClient gatewayClient) throws InterruptedException {
        GatewayClientHandler.setListening(context, gatewayClient);
        new GatewayClientHandler(context).startServices(context);
    }

}

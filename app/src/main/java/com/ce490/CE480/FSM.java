package com.ce490.CE480;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Chun-Wei Tseng on 2015/9/4.
 */
public class FSM {
    //fast testing
//    private static final long WAIT_THREE_HOURS =  30 * 1000;
//    private static final long WAIT_TO_CHECK_WIFI = 10 * 1000;
//    private static final long WAIT_CHECK_WIFI_STRENGTH = 10 * 1000;
    private static final long WAIT_THREE_HOURS = 3 * 60 * 60 * 1000;
    private static final long WAIT_TO_CHECK_WIFI = 60 * 1000;
    private static final long WAIT_CHECK_WIFI_STRENGTH = 3 * 60 * 1000;
    public static final long within = 24 * 60 * 60 * 1000;
    public static final  int MAXRETRY = 5;

    public static final  String IDLE = "Idle";
    public static final  String CHECKINGFILES = "Checking Files";
    public static final  String ATTEMPTINGUPLOAD = "Attempting Upload";
    public static final  String TRYINGCONNECTION = "Trying Connections";
    public static final  String UPLOADINPROGRESS = "Uploading...";
    private static final String SUFFIX = "/";

    //    private static String folderToCheck;
    private File mainFolder;
    public String currentStatus = "";
    private static int failCount = 0;
    private static int wifiCHeckingCount = 0;
    private Context context;

    private static File currentProcessingFile;
    private static boolean wifiSingalGood;
    public static UploadTask currentUploadTask;
    public static Handler UIHandler = new Handler(Looper.getMainLooper());
    public static WifiManager wifiManager;
    public static ConnectivityManager connManager;

    boolean testingFlag = true;

    public FSM(Context context, File folder) {
        this.context = context;
        mainFolder = folder;
        changeStatus(currentStatus);
        if (folder.exists() && folder.isDirectory()) {
            appendToConsole("FSM Folder = " + folder.getName());
        }
        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }


    private void changeStatus(String currentStatus) {
        MainActivity.status.setText(currentStatus);
    }

    public static void appendToConsole(final String toAppend) {
        UIHandler.post(new Runnable() {
            @Override
            public void run() {
                MainActivity.console.append(toAppend + "\n");
            }
        });
    }

    public static void setProgressBar(final boolean visible) {
        UIHandler.post(new Runnable() {
            @Override
            public void run() {
                int visibility;
                if (visible) {
                    visibility = View.VISIBLE;
                } else {
                    visibility = View.GONE;
                }

                MainActivity.uploadIndicater.setVisibility(visibility);
            }
        });
    }


    public void Start() {
        currentStatus = CHECKINGFILES;
        nextState();
    }

    public void nextState() {
        changeStatus(currentStatus);
        appendToConsole("\n");
        appendToConsole("Status Update: " + currentStatus);

        if (currentStatus.equals(IDLE)) {
            setProgressBar(false);
            failCount = 0;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            String currentDateAndTime = sdf.format(new Date());
            appendToConsole("Idle for Three hours start: " + currentDateAndTime);
            UIHandler.postDelayed(new Runnable() {
                public void run() {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                    String currentDateAndTime = sdf.format(new Date());
                    appendToConsole("Idle for Three hours done: " + currentDateAndTime);
                    currentStatus = CHECKINGFILES;
                    nextState();
                }
//            }, (3 * 60 * 60 * 1000));
            }, WAIT_THREE_HOURS);
        } else if (currentStatus.equals(CHECKINGFILES)) {
            if (checkNewFiles()) {
                currentStatus = ATTEMPTINGUPLOAD;
                nextState();
            } else {
                currentStatus = IDLE;
                nextState();
            }
        } else if (currentStatus.equals(ATTEMPTINGUPLOAD)) {
            appendToConsole("Verifying WIFI Connection...");
            UIHandler.postDelayed(new Runnable() {
                public void run() {
                    if (checkingWIFI()) {
                        appendToConsole("Found WIFI Connection");
                        currentStatus = TRYINGCONNECTION;
                        nextState();
                    } else {
                        appendToConsole("No WIFI Connection");
                        failCount++;
                        appendToConsole("Retrying ..." + failCount);
                        if (failCount >= MAXRETRY) {
                            appendToConsole("Max Retry reached ..." + failCount);
                            currentStatus = IDLE;
                        }
                        nextState();
                    }
                }
            },  WAIT_TO_CHECK_WIFI);

        } else if (currentStatus.equals(TRYINGCONNECTION)) {
            appendToConsole("Testing Connection...");

                UIHandler.postDelayed(new Runnable() {
                    public void run() {
                        if (isWIFIGood()) {
                            appendToConsole("Connection is good");
                            currentStatus = UPLOADINPROGRESS;
                            nextState();
                        } else {
                            appendToConsole("Connection is Bad...");
                            currentStatus = ATTEMPTINGUPLOAD;
                            nextState();
                        }
                    }
                }, WAIT_CHECK_WIFI_STRENGTH);
       } else if (currentStatus.equals(UPLOADINPROGRESS)) {
            setProgressBar(true);
            currentUploadTask = new UploadTask();
            currentUploadTask.execute();
        }
    }

    private void deleteOldFolders() {
        appendToConsole("Updating folder structure: ");
        File[] folders = mainFolder.listFiles();
        appendToConsole("There are "  + folders.length + " folders in the main directory");
        if (folders.length > 0) {
            for (int i = 0; i < folders.length; i++) {
                String folderDate = folders[i].getName();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd");
                Date convertedDate = null;
                try {
                    convertedDate = dateFormat.parse(folderDate);
                    appendToConsole("Folder: " + folders[i].getName() + " Created Date :\n" + convertedDate.toString());
                } catch (Exception e) {
                    appendToConsole(e.toString());
                }
                boolean moreThan = Math.abs(convertedDate.getTime() - new Date().getTime()) > within;
                if (moreThan) {
                    Util.DeleteRecursive(folders[i]);
                    appendToConsole("Folder is more than 24 hours old\ndelete folder: " + folders[i].getName());
                }
                folders[i].delete();
            }
        }

    }

    private boolean checkNewFiles() {
        deleteOldFolders();

        File[] folders = mainFolder.listFiles();
       if (folders.length > 0 && folders[0] != null) {
            appendToConsole("Checking for new files");
            File[] filesInDir = folders[0].listFiles();
            appendToConsole(filesInDir.length + " files found in : " + folders[0].getName());
            if (filesInDir.length > 0) {
                currentProcessingFile = filesInDir[0];
                DataManager dm = new DataManager(currentProcessingFile, context);
                if (!dm.getState().equals(DataManager.COMPLETE) && !dm.getState().equals(DataManager.UPLOADING)) {
                    dm.setState(DataManager.NEW);
                    appendToConsole("New File to Upload: " + currentProcessingFile.getName());
                }
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private boolean checkingWIFI() {
//        //test
//        return true;

        boolean isWIFI = false;

        NetworkInfo wifiChecker = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        if (wifiChecker.isConnected()) {
            isWIFI = true;
        }
        return isWIFI ;

    }

    private boolean isWIFIGood() {

        //test
//        int wifiSignalStrength = 4;
//        appendToConsole("WIFI signal RSSI: " + 200);

        WifiInfo info = wifiManager.getConnectionInfo();
        int wifiSignalStrength = WifiManager.calculateSignalLevel(info.getRssi(), 5);
        appendToConsole("WIFI Strength level " + (wifiSignalStrength + 1) + " out of 5\nIEEE 802.11 RSSI: " + info.getRssi());

        if (wifiSignalStrength >= 3) {
            appendToConsole("WIFI signal strength is good");
            return true;
        } else {
            appendToConsole("WIFI signal strength is not good");
            return false;
        }
    }

    public class UploadTask extends AsyncTask<Void, Void, Void> {

        //  https://mobile.awsblog.com/post/Tx2KF0YUQITA164/AWS-SDK-for-Android-Transfer-Manager-to-Transfer-Utility-Migration-Guide
        TransferUtility transferUtility;
        TransferObserver observer;
        public boolean complete = false;
        DataManager dm;

        @Override
        protected Void doInBackground(Void... params) {

            AmazonS3Client s3Client = Util.getS3Client(context);

            String bucketName = "ce480";
            for (Bucket bucket : s3Client.listBuckets()) {
                if (!bucket.getName().equals(bucketName)) {
                    s3Client.createBucket(bucketName);
                }

                appendToConsole("Bucket Found: " + bucket.getName());
            }

            SimpleDateFormat dataSdf = new SimpleDateFormat("yyyy_MM_dd");
            String currentDate = dataSdf.format(new Date());

            createFolder(bucketName, currentDate, s3Client);

//            SimpleDateFormat timeSdf = new SimpleDateFormat("HH");
//            String currentDateAndTime = timeSdf.format(new Date());


            String fileName = currentDate + SUFFIX + currentProcessingFile.getName();
            appendToConsole("File : " + fileName);


            transferUtility = Util.getTransferUtility(context);


//            List<TransferObserver> observers = transferUtility.getTransfersWithType(TransferType.UPLOAD);

            observer = transferUtility.upload(bucketName, fileName, currentProcessingFile.getAbsoluteFile());
//            observers.add(observer);
//            HashMap<String, Object> map = new HashMap<String, Object>();
//            Util.fillMap(map, observer, false);
//            transferRecordMaps.add(map);
            observer.setTransferListener(new UploadListener());

            dm = new DataManager(currentProcessingFile, context);
            dm.setID(observer.getId());
            dm.setState(DataManager.UPLOADING);

            appendToConsole("Uploading in progress");
            appendToConsole("AWS S3 ID: " + observer.getId());
            appendToConsole("FilePath:\n" + observer.getAbsoluteFilePath());


//            appendToConsole("State: " + observer.getState());


//            PutObjectRequest por = new PutObjectRequest(bucketName, fileName, testFile.getAbsoluteFile());
//            por.withCannedAcl(CannedAccessControlList.PublicRead);
//            PutObjectResult putResponse = s3Client.putObject(por);
//            appendToConsole("Upload with Etag: " + putResponse.getETag());

//            GetObjectRequest getRequest = new GetObjectRequest(bucketName, fileName);
//            S3Object getResponse = s3Client.getObject(getRequest);
//            InputStream myObjectBytes = getResponse.getObjectContent();

//             Do what you want with the object

//            try {
//                myObjectBytes.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }

            return null;
        }

        public void createFolder(String bucketName, String folderName, AmazonS3 client) {
            // create meta-data for your folder and set content-length to 0
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(0);
            // create empty content
            InputStream emptyContent = new ByteArrayInputStream(new byte[0]);
            // create a PutObjectRequest passing the folder name suffixed by /
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, folderName + SUFFIX, emptyContent, metadata);
            // send request to S3 to create folder
            client.putObject(putObjectRequest);
            appendToConsole("Folder: " + folderName);
        }

        private class UploadListener implements TransferListener {

            //            http://docs.aws.amazon.com/mobile/sdkforandroid/developerguide/s3transferutility.html#resume-a-transfer
            private String errtag = "UPLOADERROR";

            // Simply updates the UI list when notified.
            @Override
            public void onError(int id, Exception e) {
                Log.e(errtag, "Error during upload: " + id, e);
            }

            @Override
            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                updateGUI();
            }

            @Override
            public void onStateChanged(int id, TransferState newState) {
                appendToConsole("Transfer state: " + observer.getState());
                if (observer.getState().equals(TransferState.COMPLETED)) {
                    setProgressBar(false);

                    currentProcessingFile.delete();
                    appendToConsole("Transfer Finished\ndelete file: " + currentProcessingFile.getName());

                    if (currentProcessingFile.getParentFile().listFiles().length <= 0) {
                        currentProcessingFile.getParentFile().delete();
                    }
                    complete = true;
                    dm.setState("COMPLETED");
                    currentStatus = CHECKINGFILES;
                    nextState();
                }
            }
        }

        public void updateGUI() {
            if (!complete) {
                String progressBytes = "bytes: " + Util.getBytesString(observer.getBytesTransferred()) + "/" + Util.getBytesString(observer.getBytesTotal());

                int progress = (int) ((double) observer.getBytesTransferred() * 100 / observer.getBytesTotal());
                changeStatus(UPLOADINPROGRESS + "\n" + progressBytes + " => " + progress + "%");
            }

        }

        public void pause() {
            Boolean paused = transferUtility.pause(dm.getID());
            if (!paused) {
                appendToConsole("Cannot pause transfer. Pause  can only be done im IN_PROGRESS or WAITING state.");
            }
        }

        public void resume() {
            TransferObserver resumed = transferUtility.resume(dm.getID());
            if (resumed == null) {
                appendToConsole("Cannot resume transfer.  You can only resume transfers in a PAUSED state.");
                resumed.setTransferListener(new UploadListener());
                dm.setID(resumed.getId());
            }
        }

    }

}

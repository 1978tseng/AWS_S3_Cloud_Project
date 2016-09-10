package com.ce490.CE480;

import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ce480.S3Cloud.R;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Random;



public class MainActivity extends ActionBarActivity {
    //To test set true  and change target size of lines
    private static final boolean TESTING = true;
    private static final int testSize = 100000;

    private static final String UPLOAD_FOLDER = "Upload_To_AWS_S3";
    private static final int TESTINGFILENUMBER = 1;

    private Button goBtn;
    public static TextView console;
    public static TextView status;
    public static ProgressBar uploadIndicater;

    public FSM fsm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        goBtn = (Button) findViewById(R.id.goButon);
        console = (TextView) findViewById(R.id.console);
        status = (TextView) findViewById(R.id.Status);
        console.setMovementMethod(new ScrollingMovementMethod());
        uploadIndicater = (ProgressBar) findViewById(R.id.uploadProgress);
        uploadIndicater.setVisibility(View.GONE);

        goBtn.setEnabled(false);
        changeStatus("Initializing...");
        appendToConsole("Checking for System Setup");

        //Create Folder
        File folderToCheck = new File(Environment.getExternalStorageDirectory() + "/" + UPLOAD_FOLDER);
        //appendToConsole( "Folder Exists ? " + String.valueOf(folderToCheck.exists()));
        if(TESTING){
            Util.DeleteRecursive(folderToCheck);
            folderToCheck.delete();
            //appendToConsole("Folder Exists After Delete ? " + String.valueOf(folderToCheck.exists()));
            try{
                folderToCheck.mkdir();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd");
                Calendar cal = Calendar.getInstance();
                File dateFolder = new File(Environment.getExternalStorageDirectory() + "/" + UPLOAD_FOLDER + "/" + dateFormat.format(cal.getTime()));
//                File dateFolder = new File(Environment.getExternalStorageDirectory() + "/" + UPLOAD_FOLDER + "/" + "2013_01_01");
                dateFolder.mkdir();
                generateTestFiles(TESTINGFILENUMBER, dateFolder);

                if(folderToCheck.exists() && folderToCheck.isDirectory()) {
                    fsm = new FSM(getApplicationContext(), folderToCheck);
                    goBtn.setEnabled(true);
                }


            } catch(Exception e){
                appendToConsole("The System must have a upload folder at : " + folderToCheck.getAbsolutePath());
                appendToConsole("Android cannot create it due to exception: " + e.toString());
                appendToConsole("Please verify and restart the app after folder with files are created");
            }
        } else {

            if (!folderToCheck.exists()) {
                appendToConsole("Initialize System:\n" +
                                "Creating folder on External Storage (SD Card): at" +
                                "\n" + folderToCheck);
                try{
                    folderToCheck.mkdir();
                    appendToConsole("Folder created:\n" +
                                    "please move files needed to be upload to the directory on the SD Card Folder:" +
                                    "\n" + folderToCheck +
                                    "\nRestart the app after done");
                } catch(Exception e){
                    appendToConsole("The System must have a upload folder at : " + folderToCheck);
                    appendToConsole("Android cannot create it due to exception: " + e.toString());
                    appendToConsole("Please verify and restart the app with folder with files");
                }
            } else {
                fsm = new FSM(getApplicationContext(), folderToCheck);
                goBtn.setEnabled(true);
            }
        }



        goBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
//                appendToConsole("\nFSM Start ...")
                appendToConsole("\nStart");
                fsm.Start();
                goBtn.setEnabled(false);
            }
        });


    }



    private void generateTestFiles(int numbers, File folderToCheck) {

        for(int i = 0 ;i < numbers; i ++ ){
            addTestFile((i+1),folderToCheck);
        }
    }

    public void appendToConsole(final String toAppend) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                console.append(toAppend + "\n");
            }
        });
    }

    public void changeStatus(final String currentStatus) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                status.setText(currentStatus);
            }
        });
    }

    private void addTestFile(int seg,File dir) {

        SimpleDateFormat dateFormat = new SimpleDateFormat("HH_mm_ss");
        Calendar cal = Calendar.getInstance();
        String fileName = dateFormat.format(cal.getTime()) + "-Seg-" + seg + "-comp";

        File testFile = new File(dir.getAbsolutePath(), fileName);

        try {
            testFile.createNewFile();
            FileWriter fw = new FileWriter(testFile);
            for (int i = 0; i < testSize; i++) {
                String getLine = getDummyDataLine();
                fw.write(getLine);
                fw.flush();
            }
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        appendToConsole("File generated : " + testFile.getName() + " size: " + Util.getBytesString(testFile.length()) + " exist: " + testFile.exists());
    }

    public String getDummyDataLine() {
        //IDH: 00, IDL: 80, Len: 07, Data: 05 08 ,TS: 235957
        String dummyData = "IDH: " + String.format("%02d", getIntRandom(2))
                + ", IDL: " + String.format("%02d", getIntRandom(2))
                + ", Len: " + String.format("%02d", getIntRandom(2))
                + ", Data: " + String.format("%02d", getIntRandom(2)) + " " + String.format("%02d", getIntRandom(2))
                + ", TS:" + String.format("%06d", getIntRandom(6)) + "\n";
        return dummyData;
    }

    private int getIntRandom(int i) {
        Random rand = new Random();
        Double digits = Math.pow(10, i);
        return rand.nextInt(digits.intValue());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }



}

package com.ce490.CE480;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.io.File;

/**
 * Created by Chun-Wei Tseng on 2015/9/13.
 */
public class DataManager {

    public static final String COMPLETE = "Complete";
    public static final String UPLOADING = "uploading";
    public static final String NEW = "new";

    public static File data ;
    public static Context context;
    public static String identifier;
    public static SharedPreferences preferences;
    public static SharedPreferences.Editor editor;

    public DataManager(File toSave,Context ctxt){
        data = toSave;
        context = ctxt;
        identifier = toSave.getName();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        editor = preferences.edit();
    }

    public void setID(int id){
        setValue(identifier + "_ID" , String.valueOf(id));
    }

    public int getID(){
        return Integer.valueOf(getValue(identifier + "_ID"));
    }

    public void setState(String state){
        setValue(identifier + "_STATE" , state);
    }

    public String getState(){
        return getValue( identifier + "_STATE");
    }

    public void setValue(String key,String value){
        editor.putString(key,value);
        editor.apply();
    }

    public String getValue(String key){
         return preferences.getString(key, "");
    }


}

package com.genus.usb_comm;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class SavedPreference {
    public static SharedPreferences.Editor editor;
    public static SharedPreferences spf;

    public static void initPref(Context context) {
        spf = PreferenceManager.getDefaultSharedPreferences(context);
        editor = spf.edit();
    }

    public static void ClearSPF() {

        editor.clear();
        editor.apply();
    }

    public static int getBaudRate() {
        return spf.getInt("baudRate", 0);
    }

    public static void setBaudRate(int selected) {

        editor.putInt("baudRate", selected);
        editor.apply();
    }
    public static int getMTU() {
        return spf.getInt("mtu", 65);
    }

    public static void setMTU(int mtu) {
        editor.putInt("mtu", mtu);
        editor.apply();
    }
    public static int getBleStatus() {
        return spf.getInt("blestatus", 0);
    }

    public static void setBleStatus(int port) {
        editor.putInt("blestatus", port);
        editor.apply();
    }
}

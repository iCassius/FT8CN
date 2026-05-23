package com.bg7yoz.ft8cn.grid_tracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import org.osmdroid.tileprovider.IRegisterReceiver;

public class SafeSimpleRegisterReceiver implements IRegisterReceiver {
    private Context mContext;

    public SafeSimpleRegisterReceiver(Context context) {
        mContext = context;
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return mContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            return mContext.registerReceiver(receiver, filter);
        }
    }

    @Override
    public void unregisterReceiver(BroadcastReceiver receiver) {
        mContext.unregisterReceiver(receiver);
    }

    @Override
    public void destroy() {
        mContext = null;
    }
}

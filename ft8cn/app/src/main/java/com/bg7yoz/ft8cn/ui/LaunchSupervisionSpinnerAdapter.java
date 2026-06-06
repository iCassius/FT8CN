package com.bg7yoz.ft8cn.ui;

import android.content.Context;
import android.widget.ArrayAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * 发射监管列表适配器 (现代化 M3 版本)。
 */
public class LaunchSupervisionSpinnerAdapter extends ArrayAdapter<String> {
    private final List<Integer> timeOutList = new ArrayList<>();

    public LaunchSupervisionSpinnerAdapter(Context context) {
        super(context, android.R.layout.simple_dropdown_item_1line);
        int[] timeouts = {0, 1, 2, 3, 4, 5, 10, 15, 20, 25, 30};
        for (int t : timeouts) {
            timeOutList.add(t);
            add(String.valueOf(t));
        }
    }

    public int getTimeOut(int position) {
        return timeOutList.get(position);
    }

    public int getPosition(int timeOut) {
        return timeOutList.indexOf(timeOut);
    }
}

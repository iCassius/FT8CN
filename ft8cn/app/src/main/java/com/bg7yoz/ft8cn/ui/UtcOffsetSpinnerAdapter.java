package com.bg7yoz.ft8cn.ui;

import android.content.Context;
import android.widget.ArrayAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * 时间偏移列表适配器 (现代化 M3 版本)。
 */
public class UtcOffsetSpinnerAdapter extends ArrayAdapter<String> {
    private final List<Integer> offsetTime = new ArrayList<>();

    public UtcOffsetSpinnerAdapter(Context context) {
        super(context, android.R.layout.simple_dropdown_item_1line);
        for (int i = -14; i <= 14; i++) {
            offsetTime.add(i);
            add(String.valueOf(i));
        }
    }

    public int getPositionByValue(int value) {
        return offsetTime.indexOf(value);
    }
}

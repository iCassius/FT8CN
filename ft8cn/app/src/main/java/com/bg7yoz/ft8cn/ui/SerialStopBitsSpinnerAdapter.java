package com.bg7yoz.ft8cn.ui;

import android.content.Context;
import android.widget.ArrayAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * 串口停止位列表适配器 (现代化 M3 版本)。
 */
public class SerialStopBitsSpinnerAdapter extends ArrayAdapter<String> {
    private final int[] stopBits = {1, 2, 3};
    private final String[] stopBitsStr = {"1", "2", "1.5"};

    public SerialStopBitsSpinnerAdapter(Context context) {
        super(context, android.R.layout.simple_dropdown_item_1line, getStopBitsList());
    }

    private static List<String> getStopBitsList() {
        String[] strs = {"1", "2", "1.5"};
        List<String> list = new ArrayList<>();
        for (String s : strs) {
            list.add(s);
        }
        return list;
    }

    public int getPosition(int bits) {
        for (int i = 0; i < stopBits.length; i++) {
            if (stopBits[i] == bits) return i;
        }
        return -1;
    }

    public int getValue(int position) {
        return stopBits[position];
    }
}

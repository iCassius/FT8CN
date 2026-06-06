package com.bg7yoz.ft8cn.ui;

import android.content.Context;
import android.widget.ArrayAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * 串口数据位列表适配器 (现代化 M3 版本)。
 */
public class SerialDataBitsSpinnerAdapter extends ArrayAdapter<String> {
    private final int[] dataBits = {5, 6, 7, 8};

    public SerialDataBitsSpinnerAdapter(Context context) {
        super(context, android.R.layout.simple_dropdown_item_1line, getDataBitsList());
    }

    private static List<String> getDataBitsList() {
        int[] bits = {5, 6, 7, 8};
        List<String> list = new ArrayList<>();
        for (int b : bits) {
            list.add(String.valueOf(b));
        }
        return list;
    }

    public int getPosition(int bits) {
        for (int i = 0; i < dataBits.length; i++) {
            if (dataBits[i] == bits) return i;
        }
        return -1;
    }

    public int getValue(int position) {
        return dataBits[position];
    }
}

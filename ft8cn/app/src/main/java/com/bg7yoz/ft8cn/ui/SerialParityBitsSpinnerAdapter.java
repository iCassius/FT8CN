package com.bg7yoz.ft8cn.ui;

import android.content.Context;
import android.widget.ArrayAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * 串口校验位列表适配器 (现代化 M3 版本)。
 */
public class SerialParityBitsSpinnerAdapter extends ArrayAdapter<String> {
    private final int[] parityBits = {0, 1, 2, 3, 4};
    private final String[] parityStr = {"NONE", "ODD", "EVEN", "MARK", "SPACE"};

    public SerialParityBitsSpinnerAdapter(Context context) {
        super(context, android.R.layout.simple_dropdown_item_1line, getParityList());
    }

    private static List<String> getParityList() {
        String[] strs = {"NONE", "ODD", "EVEN", "MARK", "SPACE"};
        List<String> list = new ArrayList<>();
        for (String s : strs) {
            list.add(s);
        }
        return list;
    }

    public int getPosition(int parity) {
        for (int i = 0; i < parityBits.length; i++) {
            if (parityBits[i] == parity) return i;
        }
        return -1;
    }

    public int getValue(int position) {
        return parityBits[position];
    }
}

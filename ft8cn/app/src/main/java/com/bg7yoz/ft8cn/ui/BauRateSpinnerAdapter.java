package com.bg7yoz.ft8cn.ui;

import android.content.Context;
import android.widget.ArrayAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * 波特率列表适配器 (现代化 M3 版本)。
 */
public class BauRateSpinnerAdapter extends ArrayAdapter<String> {
    private final int[] bauRates = {1200, 2400, 4800, 9600, 14400, 19200, 38400, 57600, 115200, 128000};

    public BauRateSpinnerAdapter(Context context) {
        super(context, android.R.layout.simple_dropdown_item_1line, getBauRateList());
    }

    private static List<String> getBauRateList() {
        int[] rates = {1200, 2400, 4800, 9600, 14400, 19200, 38400, 57600, 115200, 128000};
        List<String> list = new ArrayList<>();
        for (int r : rates) {
            list.add(String.valueOf(r));
        }
        return list;
    }

    public int getPosition(int baudRate) {
        for (int i = 0; i < bauRates.length; i++) {
            if (bauRates[i] == baudRate) return i;
        }
        return -1;
    }

    public int getValue(int position) {
        return bauRates[position];
    }
}

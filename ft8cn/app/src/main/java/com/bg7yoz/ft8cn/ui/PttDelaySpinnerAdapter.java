package com.bg7yoz.ft8cn.ui;

import android.content.Context;
import android.widget.ArrayAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * PTT 延迟列表适配器 (现代化 M3 版本)。
 */
public class PttDelaySpinnerAdapter extends ArrayAdapter<String> {
    public PttDelaySpinnerAdapter(Context context) {
        super(context, android.R.layout.simple_dropdown_item_1line, getDelayList());
    }

    private static List<String> getDelayList() {
        List<String> list = new ArrayList<>();
        for (int i = 0; i <= 200; i += 10) {
            list.add(String.valueOf(i));
        }
        return list;
    }
}

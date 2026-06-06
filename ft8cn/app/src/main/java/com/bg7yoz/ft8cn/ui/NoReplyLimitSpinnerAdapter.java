package com.bg7yoz.ft8cn.ui;

import android.content.Context;
import android.widget.ArrayAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * 无回应限制列表适配器 (现代化 M3 版本)。
 */
public class NoReplyLimitSpinnerAdapter extends ArrayAdapter<String> {
    public NoReplyLimitSpinnerAdapter(Context context) {
        super(context, android.R.layout.simple_dropdown_item_1line, getLimitList());
    }

    private static List<String> getLimitList() {
        List<String> list = new ArrayList<>();
        for (int i = 0; i <= 20; i++) {
            list.add(String.valueOf(i));
        }
        return list;
    }
}

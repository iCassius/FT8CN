package com.bg7yoz.ft8cn.ui;

import android.content.Context;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;

import com.bg7yoz.ft8cn.database.OperationBand;

import java.util.ArrayList;
import java.util.List;

/**
 * 频段列表界面适配器 (现代化 M3 版本，继承自 ArrayAdapter 以支持 AutoCompleteTextView)。
 */
public class BandsSpinnerAdapter extends ArrayAdapter<String> {
    public BandsSpinnerAdapter(Context context) {
        super(context, android.R.layout.simple_dropdown_item_1line, getBandInfoList());
    }

    private static List<String> getBandInfoList() {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < OperationBand.bandList.size(); i++) {
            list.add(OperationBand.getBandInfo(i));
        }
        return list;
    }
}

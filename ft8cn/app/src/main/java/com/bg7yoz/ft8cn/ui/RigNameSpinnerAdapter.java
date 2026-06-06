package com.bg7yoz.ft8cn.ui;

import android.content.Context;
import android.widget.ArrayAdapter;

import com.bg7yoz.ft8cn.database.RigNameList;

import java.util.ArrayList;
import java.util.List;

/**
 * 电台型号列表适配器 (现代化 M3 版本)。
 */
public class RigNameSpinnerAdapter extends ArrayAdapter<String> {
    private final RigNameList rigNameList;

    public RigNameSpinnerAdapter(Context context) {
        super(context, android.R.layout.simple_dropdown_item_1line);
        rigNameList = RigNameList.getInstance(context);
        addAll(getRigNames());
    }

    private List<String> getRigNames() {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < rigNameList.rigList.size(); i++) {
            list.add(rigNameList.rigList.get(i).getName());
        }
        return list;
    }

    public RigNameList.RigName getRigName(int position) {
        return rigNameList.rigList.get(position);
    }
}

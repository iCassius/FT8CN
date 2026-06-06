package com.bg7yoz.ft8cn.ui;

import android.content.Context;

import com.bg7yoz.ft8cn.GeneralVariables;
import com.bg7yoz.ft8cn.MainViewModel;
import com.bg7yoz.ft8cn.R;
import com.bg7yoz.ft8cn.database.ControlMode;
import com.bg7yoz.ft8cn.database.OperationBand;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 快速切换频率的对话框（现代化 M3 版本）。
 * 使用 MaterialAlertDialogBuilder 替换旧的自定义 Dialog。
 */
public class FreqDialog {
    private final Context context;
    private final MainViewModel mainViewModel;

    public FreqDialog(Context context, MainViewModel mainViewModel) {
        this.context = context;
        this.mainViewModel = mainViewModel;
    }

    public void show() {
        String[] items = new String[OperationBand.bandList.size()];
        int checkedItem = -1;
        for (int i = 0; i < OperationBand.bandList.size(); i++) {
            items[i] = OperationBand.getBandInfo(i);
            if (OperationBand.getBandFreq(i) == GeneralVariables.band) {
                checkedItem = i;
            }
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.operationBand)
                .setSingleChoiceItems(items, checkedItem, (dialog, which) -> {
                    long band = OperationBand.getBandFreq(which);
                    GeneralVariables.bandListIndex = which;
                    GeneralVariables.band = band;

                    mainViewModel.databaseOpr.getAllQSLCallsigns();
                    mainViewModel.databaseOpr.writeConfig("bandFreq", String.valueOf(band), null);

                    if (GeneralVariables.controlMode == ControlMode.CAT
                            || GeneralVariables.controlMode == ControlMode.RTS
                            || GeneralVariables.controlMode == ControlMode.DTR) {
                        mainViewModel.setOperationBand();
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}

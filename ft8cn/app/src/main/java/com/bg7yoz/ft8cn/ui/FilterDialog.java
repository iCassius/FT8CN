package com.bg7yoz.ft8cn.ui;

import android.content.Context;

import com.bg7yoz.ft8cn.MainViewModel;
import com.bg7yoz.ft8cn.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 日志查询的过滤对话框（现代化 M3 版本）。
 * 使用 MaterialAlertDialogBuilder 替换旧的自定义 Dialog。
 */
public class FilterDialog {
    private final Context context;
    private final MainViewModel mainViewModel;

    public FilterDialog(Context context, MainViewModel mainViewModel) {
        this.context = context;
        this.mainViewModel = mainViewModel;
    }

    public void show() {
        String[] items = {
                context.getString(R.string.filter_all),
                context.getString(R.string.filter_is_qsl),
                context.getString(R.string.filter_none_qsl)
        };

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.pl_filter)
                .setSingleChoiceItems(items, mainViewModel.queryFilter, (dialog, which) -> {
                    mainViewModel.queryFilter = which;
                    mainViewModel.mutableQueryFilter.postValue(which);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}

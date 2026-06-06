package com.bg7yoz.ft8cn.ui;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.bg7yoz.ft8cn.BuildConfig;
import com.bg7yoz.ft8cn.R;
import com.bg7yoz.ft8cn.database.DatabaseOpr;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 清除缓存数据的对话框（现代化 M3 版本）。
 * 使用 MaterialAlertDialogBuilder 替换旧的自定义 Dialog。
 */
public class ClearCacheDataDialog {
    public enum CACHE_MODE {FOLLOW_DATA, SWL_MSG, SWL_QSO}

    private final Context context;
    private final CACHE_MODE cache_mode;
    private final DatabaseOpr db;

    public ClearCacheDataDialog(@NonNull Context context, Activity activity, DatabaseOpr db, CACHE_MODE cache_mode) {
        this.context = context;
        this.db = db;
        this.cache_mode = cache_mode;
    }

    public void show() {
        View view = LayoutInflater.from(context).inflate(R.layout.clear_cache_dialog_layout, null);
        TextView cacheHelpMessage = view.findViewById(R.id.cacheHelpMessage);
        TextView appNameTextView = view.findViewById(R.id.appNameTextView);
        TextView buildVersionTextView = view.findViewById(R.id.buildVersionTextView);
        View clearCacheButton = view.findViewById(R.id.clearCacheButton);

        // 隐藏旧布局中的不必要元素
        view.findViewById(R.id.scrollUpImageView).setVisibility(View.GONE);
        view.findViewById(R.id.scrollDownImageView).setVisibility(View.GONE);
        view.findViewById(R.id.cancelClearButton).setVisibility(View.GONE); // 使用 Dialog 的 NegativeButton

        appNameTextView.setText(R.string.app_name);
        buildVersionTextView.setText(BuildConfig.VERSION_NAME);

        String msg = "";
        switch (cache_mode) {
            case FOLLOW_DATA: msg = context.getString(R.string.maidenhead_help); break;
            case SWL_MSG: msg = context.getString(R.string.message_mode_help); break;
            case SWL_QSO: msg = context.getString(R.string.nav_menu_title_history); break;
        }
        cacheHelpMessage.setText(msg);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.delete)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .show();

        clearCacheButton.setOnClickListener(v -> {
            switch (cache_mode) {
                case FOLLOW_DATA: db.clearFollowCallsigns(); break;
                case SWL_MSG: db.clearLogCacheData(); break;
                case SWL_QSO: db.clearSWLQsoData(); break;
            }
            dialog.dismiss();
        });
    }
}

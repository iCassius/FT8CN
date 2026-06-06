package com.bg7yoz.ft8cn.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.bg7yoz.ft8cn.GeneralVariables;
import com.bg7yoz.ft8cn.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * 帮助信息的对话框 (现代化 M3 版本)。
 * 自动适配屏幕 80% 大小，采用全屏风格化背景。
 */
public class HelpDialog {
    private final Context context;
    private final Activity activity;
    private final String msg;

    public HelpDialog(@NonNull Context context, Activity activity, String str, boolean fromFile) {
        this.context = context;
        this.activity = activity;
        if (fromFile) {
            msg = getTextFromAssets(str);
        } else {
            msg = str;
        }
    }

    public void show() {
        View view = LayoutInflater.from(context).inflate(R.layout.help_dialog_layout, null);
        TextView messageTextView = view.findViewById(R.id.helpMessage);
        TextView appNameTextView = view.findViewById(R.id.appNameTextView);
        TextView buildVersionTextView = view.findViewById(R.id.buildVersionTextView);
        View getNewButton = view.findViewById(R.id.getNewVersionButton);

        messageTextView.setText(msg);
        appNameTextView.setText(R.string.app_name);
        buildVersionTextView.setText(String.format("%s (%s)", GeneralVariables.VERSION, GeneralVariables.BUILD_DATE));

        getNewButton.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setAction("android.intent.action.VIEW");
            intent.setData(Uri.parse("https://github.com/n0pra/ft8cn/releases"));
            context.startActivity(intent);
        });

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        dialog.show();

        // 设置窗口尺寸为屏幕的 80%
        Window window = dialog.getWindow();
        if (window != null) {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(window.getAttributes());
            layoutParams.width = (int) (displayMetrics.widthPixels * 0.85);
            layoutParams.height = (int) (displayMetrics.heightPixels * 0.80);
            window.setAttributes(layoutParams);
        }
    }

    private String getTextFromAssets(String fileName) {
        StringBuilder stringBuilder = new StringBuilder();
        try (BufferedReader bf = new BufferedReader(new InputStreamReader(context.getAssets().open(fileName)))) {
            String line;
            while ((line = bf.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return stringBuilder.toString();
    }
}

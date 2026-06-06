package com.bg7yoz.ft8cn.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.bg7yoz.ft8cn.GeneralVariables;
import com.bg7yoz.ft8cn.MainViewModel;
import com.bg7yoz.ft8cn.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 设置信号输出强度的对话框（现代化 M3 版本）。
 * 使用 MaterialAlertDialogBuilder 替换旧的自定义 Dialog。
 */
public class SetVolumeDialog {
    private static final String TAG = "SetVolumeDialog";
    private final Context context;
    private final MainViewModel mainViewModel;
    private TextView volumeValueMessage;
    private VolumeProgress volumeProgress;

    public SetVolumeDialog(@NonNull Context context, MainViewModel mainViewModel) {
        this.context = context;
        this.mainViewModel = mainViewModel;
    }

    @SuppressLint("DefaultLocale")
    public void show() {
        View view = LayoutInflater.from(context).inflate(R.layout.set_volume_dialog, null);
        volumeValueMessage = view.findViewById(R.id.volumeValueMessage);
        SeekBar volumeSeekBar = view.findViewById(R.id.volumeSeekBar);
        volumeProgress = view.findViewById(R.id.volumeProgress);

        volumeProgress.setAlarmValue(1.1f);
        volumeProgress.setValueColor(context.getColor(R.color.volume_progress_value));
        
        setVolumeText(GeneralVariables.volumePercent);
        volumeSeekBar.setProgress((int) (GeneralVariables.volumePercent * 100));

        GeneralVariables.mutableVolumePercent.observeForever(this::setVolumeText);

        volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                float vol = i / 100f;
                GeneralVariables.volumePercent = vol;
                GeneralVariables.mutableVolumePercent.postValue(vol);
                mainViewModel.databaseOpr.writeConfig("volumeValue", String.valueOf(i), null);
                if (mainViewModel.baseRig != null && mainViewModel.baseRig.getConnector() != null) {
                    mainViewModel.baseRig.getConnector().setRFVolume(i);
                    Log.d(TAG, String.format("set volume:%d", i));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.volume_percent)
                .setView(view)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    @SuppressLint("DefaultLocale")
    private void setVolumeText(float vol) {
        if (volumeValueMessage != null) {
            volumeValueMessage.setText(String.format(
                    GeneralVariables.getStringFromResource(R.string.volume_percent),
                    vol * 100f));
        }
        if (volumeProgress != null) {
            volumeProgress.setPercent(vol);
        }
    }
}

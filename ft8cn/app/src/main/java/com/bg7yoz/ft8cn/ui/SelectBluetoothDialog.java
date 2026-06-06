package com.bg7yoz.ft8cn.ui;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bg7yoz.ft8cn.GeneralVariables;
import com.bg7yoz.ft8cn.MainViewModel;
import com.bg7yoz.ft8cn.R;
import com.bg7yoz.ft8cn.bluetooth.BluetoothConstants;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;

/**
 * 蓝牙设备选择对话框（现代化 M3 版本）。
 * 使用 MaterialAlertDialogBuilder 替换旧的自定义 Dialog。
 */
public class SelectBluetoothDialog {
    static class BluetoothDeviceInfo {
        BluetoothDevice device;
        boolean isSPP;
        boolean isHeadSet;

        public BluetoothDeviceInfo(BluetoothDevice device, boolean isSPP, boolean isHeadSet) {
            this.device = device;
            this.isSPP = isSPP;
            this.isHeadSet = isHeadSet;
        }
    }

    private final Context context;
    private final MainViewModel mainViewModel;
    private final ArrayList<BluetoothDeviceInfo> devices = new ArrayList<>();
    private androidx.appcompat.app.AlertDialog dialog;

    public SelectBluetoothDialog(@NonNull Context context, MainViewModel mainViewModel) {
        this.context = context;
        this.mainViewModel = mainViewModel;
    }

    @SuppressLint({"MissingPermission", "NotifyDataSetChanged"})
    private void getBluetoothDevice(BluetoothDevicesAdapter adapter) {
        devices.clear();
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            return;
        }
        for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
            boolean isHeadset = BluetoothConstants.checkIsHeadSet(device);
            boolean isSpp = BluetoothConstants.checkIsSpp(device);
            if (isSpp) {
                devices.add(0, new BluetoothDeviceInfo(device, true, isHeadset));
            } else if (isHeadset) {
                devices.add(new BluetoothDeviceInfo(device, false, true));
            }
        }
        adapter.notifyDataSetChanged();
    }

    public void show() {
        View view = LayoutInflater.from(context).inflate(R.layout.select_bluetooth_dialog_layout, null);
        RecyclerView devicesRecyclerView = view.findViewById(R.id.bluetoothListRecyclerView);
        
        // 隐藏旧布局中的控制图标
        view.findViewById(R.id.bluetoothScrollUpImageView).setVisibility(View.GONE);
        view.findViewById(R.id.bluetoothScrollDownImageView).setVisibility(View.GONE);

        BluetoothDevicesAdapter adapter = new BluetoothDevicesAdapter();
        devicesRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        devicesRecyclerView.setAdapter(adapter);

        getBluetoothDevice(adapter);

        dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.pl_select_bluetooth)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    class BluetoothDevicesAdapter extends RecyclerView.Adapter<BluetoothDevicesAdapter.BluetoothHolder> {

        @NonNull
        @Override
        public BluetoothHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.bluetooth_device_list_item, parent, false);
            return new BluetoothHolder(view);
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onBindViewHolder(@NonNull BluetoothHolder holder, int position) {
            BluetoothDeviceInfo deviceInfo = devices.get(position);
            holder.deviceInfo = deviceInfo;
            holder.bluetoothNameTextView.setText(deviceInfo.device.getName());
            
            int colorRes = deviceInfo.isSPP ? R.color.bluetooth_device_enable_color : R.color.bluetooth_device_disable_color;
            holder.bluetoothNameTextView.setTextColor(context.getColor(colorRes));

            holder.headsetImageView.setVisibility(deviceInfo.isHeadSet ? View.VISIBLE : View.GONE);
            holder.sppDeviceImageView.setVisibility(deviceInfo.isSPP ? View.VISIBLE : View.GONE);
            
            holder.bluetoothAddressTextView.setText(deviceInfo.device.getAddress());

            holder.bluetoothListConstraintLayout.setOnClickListener(v -> {
                ToastMessage.show(String.format(
                        GeneralVariables.getStringFromResource(R.string.select_bluetooth_device),
                        deviceInfo.device.getName()));
                mainViewModel.connectBluetoothRig(GeneralVariables.getMainContext(), deviceInfo.device);
                if (dialog != null) dialog.dismiss();
            });
        }

        @Override
        public int getItemCount() {
            return devices.size();
        }

        class BluetoothHolder extends RecyclerView.ViewHolder {
            BluetoothDeviceInfo deviceInfo;
            TextView bluetoothNameTextView, bluetoothAddressTextView;
            ConstraintLayout bluetoothListConstraintLayout;
            ImageView headsetImageView, sppDeviceImageView;

            public BluetoothHolder(@NonNull View itemView) {
                super(itemView);
                bluetoothNameTextView = itemView.findViewById(R.id.bluetoothNameTextView);
                bluetoothAddressTextView = itemView.findViewById(R.id.bluetoothAddressTextView);
                bluetoothListConstraintLayout = itemView.findViewById(R.id.bluetoothListConstraintLayout);
                headsetImageView = itemView.findViewById(R.id.headsetImageView);
                sppDeviceImageView = itemView.findViewById(R.id.sppDeviceImageView);
            }
        }
    }
}

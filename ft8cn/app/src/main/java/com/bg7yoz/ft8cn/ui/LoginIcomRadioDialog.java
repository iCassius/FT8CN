package com.bg7yoz.ft8cn.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;

import com.bg7yoz.ft8cn.GeneralVariables;
import com.bg7yoz.ft8cn.MainViewModel;
import com.bg7yoz.ft8cn.R;
import com.bg7yoz.ft8cn.icom.IComWifiRig;
import com.bg7yoz.ft8cn.icom.XieGuWifiRig;
import com.bg7yoz.ft8cn.rigs.InstructionSet;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 网络模式登录ICOM的对话框（现代化 M3 版本）。
 * 使用 MaterialAlertDialogBuilder 替换旧的自定义 Dialog。
 */
public class LoginIcomRadioDialog {
    private final Context context;
    private final MainViewModel mainViewModel;
    private EditText inputIcomAddressEdit;
    private EditText inputIcomPortEdit;
    private EditText inputIcomUserNameEdit;
    private EditText inputIcomPasswordEdit;
    private Button icomLoginButton;
    private boolean passVisible = false;
    private androidx.appcompat.app.AlertDialog dialog;

    public LoginIcomRadioDialog(@NonNull Context context, MainViewModel mainViewModel) {
        this.context = context;
        this.mainViewModel = mainViewModel;
    }

    @SuppressLint("DefaultLocale")
    public void show() {
        View view = LayoutInflater.from(context).inflate(R.layout.login_icom_dialog_layout, null);
        inputIcomAddressEdit = view.findViewById(R.id.inputIcomAddressEdit);
        inputIcomPortEdit = view.findViewById(R.id.inputIcomPortEdit);
        inputIcomUserNameEdit = view.findViewById(R.id.inputIcomUserNameEdit);
        inputIcomPasswordEdit = view.findViewById(R.id.inputIcomPasswordEdit);
        icomLoginButton = view.findViewById(R.id.icomLoginButton);
        ImageButton showPassImageButton = view.findViewById(R.id.showPassImageButton);

        inputIcomAddressEdit.setText(GeneralVariables.icomIp);
        inputIcomPortEdit.setText(String.valueOf(GeneralVariables.icomUdpPort));
        inputIcomUserNameEdit.setText(GeneralVariables.icomUserName);
        inputIcomPasswordEdit.setText(GeneralVariables.icomPassword);
        checkInput();

        icomLoginButton.setOnClickListener(v -> {
            if (GeneralVariables.instructionSet == InstructionSet.ICOM) {
                ToastMessage.show(String.format(context.getString(R.string.connect_icom_ip), inputIcomAddressEdit.getText()));
                mainViewModel.connectWifiRig(new IComWifiRig(GeneralVariables.icomIp, GeneralVariables.icomUdpPort, GeneralVariables.icomUserName, GeneralVariables.icomPassword));
            } else if (GeneralVariables.instructionSet == InstructionSet.XIEGU_6100) {
                ToastMessage.show(String.format(context.getString(R.string.connect_xiegu_ip), inputIcomAddressEdit.getText()));
                mainViewModel.connectWifiRig(new XieGuWifiRig(GeneralVariables.icomIp, GeneralVariables.icomUdpPort, GeneralVariables.icomUserName, GeneralVariables.icomPassword));
            }
            if (dialog != null) dialog.dismiss();
        });

        setupTextWatchers();

        showPassImageButton.setOnClickListener(v -> {
            passVisible = !passVisible;
            inputIcomPasswordEdit.setTransformationMethod(passVisible ? HideReturnsTransformationMethod.getInstance() : PasswordTransformationMethod.getInstance());
        });

        dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.connectMode)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void setupTextWatchers() {
        inputIcomAddressEdit.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                checkInput();
                GeneralVariables.icomIp = editable.toString().trim();
                writeConfig("icomIp", GeneralVariables.icomIp);
            }
        });

        inputIcomPortEdit.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                checkInput();
                String portStr = editable.toString().trim();
                if (GeneralVariables.isInteger(portStr)) {
                    writeConfig("icomPort", portStr);
                    GeneralVariables.icomUdpPort = Integer.parseInt(portStr);
                }
            }
        });

        inputIcomUserNameEdit.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                checkInput();
                GeneralVariables.icomUserName = editable.toString().trim();
                writeConfig("icomUserName", GeneralVariables.icomUserName);
            }
        });

        inputIcomPasswordEdit.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                checkInput();
                GeneralVariables.icomPassword = editable.toString();
                writeConfig("icomPassword", GeneralVariables.icomPassword);
            }
        });
    }

    private void checkInput() {
        if (icomLoginButton != null) {
            icomLoginButton.setEnabled(!inputIcomAddressEdit.getText().toString().isEmpty()
                    && !inputIcomPortEdit.getText().toString().isEmpty()
                    && !inputIcomUserNameEdit.getText().toString().isEmpty()
                    && !inputIcomPasswordEdit.getText().toString().isEmpty()
                    && GeneralVariables.isInteger(inputIcomPortEdit.getText().toString().trim())
            );
        }
    }

    private void writeConfig(String KeyName, String Value) {
        mainViewModel.databaseOpr.writeConfig(KeyName, Value, null);
    }

    private static abstract class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}

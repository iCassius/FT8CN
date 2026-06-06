package com.bg7yoz.ft8cn.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bg7yoz.ft8cn.FAQActivity;
import com.bg7yoz.ft8cn.GeneralVariables;
import com.bg7yoz.ft8cn.MainViewModel;
import com.bg7yoz.ft8cn.R;
import com.bg7yoz.ft8cn.database.ControlMode;
import com.bg7yoz.ft8cn.database.OperationBand;
import com.bg7yoz.ft8cn.database.RigNameList;
import com.bg7yoz.ft8cn.databinding.FragmentConfigBinding;
import com.bg7yoz.ft8cn.log.ThirdPartyService;
import com.bg7yoz.ft8cn.maidenhead.MaidenheadGrid;
import com.bg7yoz.ft8cn.timer.UtcTimer;

import java.io.IOException;

/**
 * 设置界面 (现代化 M3 版本，功能全量找回，逻辑完全同步)。
 */
public class ConfigFragment extends Fragment {
    private MainViewModel mainViewModel;
    private FragmentConfigBinding binding;

    private BandsSpinnerAdapter bandsSpinnerAdapter;
    private BauRateSpinnerAdapter bauRateSpinnerAdapter;
    private SerialDataBitsSpinnerAdapter dataBitsSpinnerAdapter;
    private SerialParityBitsSpinnerAdapter parityBitsSpinnerAdapter;
    private SerialStopBitsSpinnerAdapter stopBitsSpinnerAdapter;
    private RigNameSpinnerAdapter rigNameSpinnerAdapter;
    private LaunchSupervisionSpinnerAdapter launchSupervisionSpinnerAdapter;
    private PttDelaySpinnerAdapter pttDelaySpinnerAdapter;
    private UtcOffsetSpinnerAdapter utcOffsetSpinnerAdapter;
    private NoReplyLimitSpinnerAdapter noReplyLimitSpinnerAdapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        binding = FragmentConfigBinding.inflate(inflater, container, false);

        initAdapters();
        initViews();
        setupListeners();
        setHelpDialog();

        return binding.getRoot();
    }

    private void initAdapters() {
        bandsSpinnerAdapter = new BandsSpinnerAdapter(requireContext());
        bauRateSpinnerAdapter = new BauRateSpinnerAdapter(requireContext());
        dataBitsSpinnerAdapter = new SerialDataBitsSpinnerAdapter(requireContext());
        parityBitsSpinnerAdapter = new SerialParityBitsSpinnerAdapter(requireContext());
        stopBitsSpinnerAdapter = new SerialStopBitsSpinnerAdapter(requireContext());
        rigNameSpinnerAdapter = new RigNameSpinnerAdapter(requireContext());
        launchSupervisionSpinnerAdapter = new LaunchSupervisionSpinnerAdapter(requireContext());
        pttDelaySpinnerAdapter = new PttDelaySpinnerAdapter(requireContext());
        utcOffsetSpinnerAdapter = new UtcOffsetSpinnerAdapter(requireContext());
        noReplyLimitSpinnerAdapter = new NoReplyLimitSpinnerAdapter(requireContext());

        binding.operationBandSpinner.setAdapter(bandsSpinnerAdapter);
        binding.baudRateSpinner.setAdapter(bauRateSpinnerAdapter);
        binding.dataBitsSpinner.setAdapter(dataBitsSpinnerAdapter);
        binding.parityBitsSpinner.setAdapter(parityBitsSpinnerAdapter);
        binding.stopBitsSpinner.setAdapter(stopBitsSpinnerAdapter);
        binding.rigNameSpinner.setAdapter(rigNameSpinnerAdapter);
        binding.launchSupervisionSpinner.setAdapter(launchSupervisionSpinnerAdapter);
        binding.pttDelayOffsetSpinner.setAdapter(pttDelaySpinnerAdapter);
        binding.utcTimeOffsetSpinner.setAdapter(utcOffsetSpinnerAdapter);
        binding.noResponseCountSpinner.setAdapter(noReplyLimitSpinnerAdapter);
    }

    private void initViews() {
        binding.inputMycallEdit.setText(GeneralVariables.myCallsign);
        binding.modifierEdit.setText(GeneralVariables.toModifier);
        binding.inputMyGridEdit.setText(GeneralVariables.getMyMaidenheadGrid());
        binding.inputFreqEditor.setText(GeneralVariables.getBaseFrequencyStr());
        binding.inputTransDelayEdit.setText(GeneralVariables.getTransmitDelayStr());
        binding.civAddressEdit.setText(GeneralVariables.getCivAddressStr());
        binding.excludedCallsignEdit.setText(GeneralVariables.getExcludeCallsigns());

        binding.enableCloudlogSwitch.setChecked(GeneralVariables.enableCloudlog);
        binding.cloudlogServerAddressEdit.setText(GeneralVariables.getCloudlogServerAddress());
        binding.cloudlogServerApiKeyEdit.setText(GeneralVariables.getCloudlogServerApiKey());
        binding.cloudlogStationIdEdit.setText(GeneralVariables.getCloudlogStationID());
        binding.enableQrzSwitch.setChecked(GeneralVariables.enableQRZ);
        binding.qrzApiKeyTextEdit.setText(GeneralVariables.getQrzApiKey());

        binding.synFrequencySwitch.setChecked(GeneralVariables.synFrequency);
        binding.inputFreqEditor.setEnabled(!GeneralVariables.synFrequency);
        binding.followCQSwitch.setChecked(GeneralVariables.autoFollowCQ);
        binding.autoCallfollowSwitch.setChecked(GeneralVariables.autoCallFollow);
        binding.saveSWLSwitch.setChecked(GeneralVariables.saveSWLMessage);
        binding.saveSWLQSOSwitch.setChecked(GeneralVariables.saveSWL_QSO);

        if (GeneralVariables.bandListIndex >= 0 && GeneralVariables.bandListIndex < OperationBand.bandList.size()) {
            binding.operationBandSpinner.setText(OperationBand.getBandInfo(GeneralVariables.bandListIndex), false);
        }
        if (GeneralVariables.modelNo >= 0 && GeneralVariables.modelNo < RigNameList.getInstance(requireContext()).rigList.size()) {
            binding.rigNameSpinner.setText(RigNameList.getInstance(requireContext()).rigList.get(GeneralVariables.modelNo).getName(), false);
        }
        binding.baudRateSpinner.setText(String.valueOf(GeneralVariables.baudRate), false);
        binding.dataBitsSpinner.setText(String.valueOf(GeneralVariables.serialDataBits), false);
        int parityPos = parityBitsSpinnerAdapter.getPosition(GeneralVariables.serialParity);
        if (parityPos >= 0) binding.parityBitsSpinner.setText(parityBitsSpinnerAdapter.getItem(parityPos), false);
        int stopPos = stopBitsSpinnerAdapter.getPosition(GeneralVariables.serialStopBits);
        if (stopPos >= 0) binding.stopBitsSpinner.setText(stopBitsSpinnerAdapter.getItem(stopPos), false);
        binding.utcTimeOffsetSpinner.setText(String.valueOf(mainViewModel.utcTimer.getTime_sec()), false);
        binding.pttDelayOffsetSpinner.setText(String.valueOf(GeneralVariables.pttDelay), false);
        binding.launchSupervisionSpinner.setText(String.valueOf(GeneralVariables.launchSupervision / 60000), false);
        binding.noResponseCountSpinner.setText(String.valueOf(GeneralVariables.noReplyLimit), false);

        updateControlModeUI();
    }

    private void setupListeners() {
        binding.inputMycallEdit.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) { GeneralVariables.myCallsign = s.toString().toUpperCase(); writeConfig("callsign", GeneralVariables.myCallsign); }
        });
        binding.modifierEdit.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) { GeneralVariables.toModifier = s.toString().toUpperCase(); writeConfig("toModifier", GeneralVariables.toModifier); }
        });
        binding.inputMyGridEdit.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) { GeneralVariables.setMyMaidenheadGrid(s.toString().toUpperCase()); writeConfig("grid", GeneralVariables.getMyMaidenheadGrid()); }
        });
        binding.inputFreqEditor.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) { try { GeneralVariables.setBaseFrequency(Float.parseFloat(s.toString())); writeConfig("freq", s.toString()); } catch (Exception ignore) {} }
        });
        binding.inputTransDelayEdit.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) { try { GeneralVariables.transmitDelay = Integer.parseInt(s.toString()); writeConfig("transDelay", s.toString()); } catch (Exception ignore) {} }
        });
        binding.civAddressEdit.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) { try { GeneralVariables.civAddress = Integer.parseInt(s.toString(), 16); writeConfig("civ", s.toString()); } catch (Exception ignore) {} }
        });
        binding.excludedCallsignEdit.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) { GeneralVariables.addExcludedCallsigns(s.toString()); writeConfig("excludedCallsigns", s.toString()); }
        });

        binding.cloudlogServerAddressEdit.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) { GeneralVariables.cloudlogServerAddress = s.toString(); writeConfig("cloudlogServerAddress", s.toString()); }
        });
        binding.cloudlogServerApiKeyEdit.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) { GeneralVariables.cloudlogApiKey = s.toString(); writeConfig("cloudlogApiKey", s.toString()); }
        });
        binding.cloudlogStationIdEdit.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) { GeneralVariables.cloudlogStationID = s.toString(); writeConfig("cloudlogStationId", s.toString()); }
        });
        binding.qrzApiKeyTextEdit.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) { GeneralVariables.qrzApiKey = s.toString(); writeConfig("qrzApiKey", s.toString()); }
        });

        binding.synFrequencySwitch.setOnCheckedChangeListener((v, isChecked) -> { GeneralVariables.synFrequency = isChecked; writeConfig("synFreq", isChecked ? "1" : "0"); binding.inputFreqEditor.setEnabled(!isChecked); });
        binding.followCQSwitch.setOnCheckedChangeListener((v, isChecked) -> { GeneralVariables.autoFollowCQ = isChecked; writeConfig("autoFollowCQ", isChecked ? "1" : "0"); });
        binding.autoCallfollowSwitch.setOnCheckedChangeListener((v, isChecked) -> { GeneralVariables.autoCallFollow = isChecked; writeConfig("autoCallFollow", isChecked ? "1" : "0"); });
        binding.saveSWLSwitch.setOnCheckedChangeListener((v, isChecked) -> { GeneralVariables.saveSWLMessage = isChecked; writeConfig("saveSWL", isChecked ? "1" : "0"); });
        binding.saveSWLQSOSwitch.setOnCheckedChangeListener((v, isChecked) -> { GeneralVariables.saveSWL_QSO = isChecked; writeConfig("saveSWLQSO", isChecked ? "1" : "0"); });
        binding.enableCloudlogSwitch.setOnCheckedChangeListener((v, isChecked) -> { GeneralVariables.enableCloudlog = isChecked; writeConfig("enableCloudlog", isChecked ? "1" : "0"); });
        binding.enableQrzSwitch.setOnCheckedChangeListener((v, isChecked) -> { GeneralVariables.enableQRZ = isChecked; writeConfig("enableQRZ", isChecked ? "1" : "0"); });

        binding.operationBandSpinner.setOnItemClickListener((parent, view, position, id) -> { GeneralVariables.bandListIndex = position; GeneralVariables.band = OperationBand.getBandFreq(position); writeConfig("bandFreq", String.valueOf(GeneralVariables.band)); if (isCatMode()) mainViewModel.setOperationBand(); });
        binding.rigNameSpinner.setOnItemClickListener((parent, view, position, id) -> { GeneralVariables.modelNo = position; writeConfig("modelNo", String.valueOf(position)); RigNameList.RigName rigName = rigNameSpinnerAdapter.getRigName(position); binding.baudRateSpinner.setText(String.valueOf(rigName.bauRate), false); GeneralVariables.baudRate = rigName.bauRate; writeConfig("baudRate", String.valueOf(rigName.bauRate)); GeneralVariables.instructionSet = rigName.instructionSet; writeConfig("instruction", String.valueOf(rigName.instructionSet)); });
        binding.baudRateSpinner.setOnItemClickListener((parent, view, position, id) -> { GeneralVariables.baudRate = bauRateSpinnerAdapter.getValue(position); writeConfig("baudRate", String.valueOf(GeneralVariables.baudRate)); });
        binding.dataBitsSpinner.setOnItemClickListener((parent, view, position, id) -> { GeneralVariables.serialDataBits = dataBitsSpinnerAdapter.getValue(position); writeConfig("dataBits", String.valueOf(GeneralVariables.serialDataBits)); });
        binding.parityBitsSpinner.setOnItemClickListener((parent, view, position, id) -> { GeneralVariables.serialParity = parityBitsSpinnerAdapter.getValue(position); writeConfig("parityBits", String.valueOf(GeneralVariables.serialParity)); });
        binding.stopBitsSpinner.setOnItemClickListener((parent, view, position, id) -> { GeneralVariables.serialStopBits = stopBitsSpinnerAdapter.getValue(position); writeConfig("stopBits", String.valueOf(GeneralVariables.serialStopBits)); });
        binding.utcTimeOffsetSpinner.setOnItemClickListener((parent, view, position, id) -> { String item = utcOffsetSpinnerAdapter.getItem(position); if (item != null) { int offset = Integer.parseInt(item); mainViewModel.utcTimer.setTime_sec(offset); } });
        binding.pttDelayOffsetSpinner.setOnItemClickListener((parent, view, position, id) -> { String item = pttDelaySpinnerAdapter.getItem(position); if (item != null) { GeneralVariables.pttDelay = Integer.parseInt(item); writeConfig("pttDelay", String.valueOf(GeneralVariables.pttDelay)); } });
        binding.launchSupervisionSpinner.setOnItemClickListener((parent, view, position, id) -> { GeneralVariables.launchSupervision = launchSupervisionSpinnerAdapter.getTimeOut(position) * 60000; writeConfig("launchSupervision", String.valueOf(GeneralVariables.launchSupervision)); });
        binding.noResponseCountSpinner.setOnItemClickListener((parent, view, position, id) -> { GeneralVariables.noReplyLimit = position; writeConfig("noReplyLimit", String.valueOf(position)); });

        binding.configGetGridImageButton.setOnClickListener(v -> { String grid = MaidenheadGrid.getMyMaidenheadGrid(getContext()); if (!grid.isEmpty()) binding.inputMyGridEdit.setText(grid); });
        binding.serialDefaultButton.setOnClickListener(v -> { GeneralVariables.serialParity = 0; GeneralVariables.serialDataBits = 8; GeneralVariables.serialStopBits = 1; initViews(); });
        binding.synTImeButton.setOnClickListener(v -> {
            binding.synTImeButton.setEnabled(false);
            binding.synTImeButton.setText(R.string.syncing);
            UtcTimer.syncTime(new UtcTimer.AfterSyncTime() {
                @SuppressLint("DefaultLocale")
                @Override public void doAfterSyncTimer(int delay) {
                    binding.synTImeButton.setEnabled(true);
                    binding.synTImeButton.setText(R.string.sync_time);
                    binding.utcTimeOffsetSpinner.setText(String.valueOf(mainViewModel.utcTimer.getTime_sec()), false);
                    ToastMessage.show(String.format("%s (偏差: %dms)", getString(R.string.config_clock_is_accurate), delay));
                }
                @Override public void syncFailed(IOException e) {
                    binding.synTImeButton.setEnabled(true);
                    binding.synTImeButton.setText(R.string.sync_time);
                    ToastMessage.show(e.getMessage());
                }
            });
        });

        binding.testCloudlogButton.setOnClickListener(v -> {
            binding.testCloudlogButton.setEnabled(false);
            binding.testCloudlogButton.setText(R.string.testing);
            new Thread(() -> {
                boolean result = ThirdPartyService.CheckCloudlogConnection();
                new Handler(Looper.getMainLooper()).post(() -> {
                    binding.testCloudlogButton.setText(result ? R.string.pass : R.string.fail);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> { binding.testCloudlogButton.setEnabled(true); binding.testCloudlogButton.setText(R.string.test); }, 2000);
                });
            }).start();
        });

        binding.testQrzButton.setOnClickListener(v -> {
            binding.testQrzButton.setEnabled(false);
            binding.testQrzButton.setText(R.string.testing);
            new Thread(() -> {
                boolean result = ThirdPartyService.CheckQRZConnection();
                new Handler(Looper.getMainLooper()).post(() -> {
                    binding.testQrzButton.setText(result ? R.string.pass : R.string.fail);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> { binding.testQrzButton.setEnabled(true); binding.testQrzButton.setText(R.string.test); }, 2000);
                });
            }).start();
        });

        binding.clearFollowButton.setOnClickListener(v -> new ClearCacheDataDialog(requireContext(), requireActivity(), mainViewModel.databaseOpr, ClearCacheDataDialog.CACHE_MODE.FOLLOW_DATA).show());
        binding.clearLogCacheButton.setOnClickListener(v -> new ClearCacheDataDialog(requireContext(), requireActivity(), mainViewModel.databaseOpr, ClearCacheDataDialog.CACHE_MODE.SWL_MSG).show());
        binding.clearSWlQsoButton.setOnClickListener(v -> new ClearCacheDataDialog(requireContext(), requireActivity(), mainViewModel.databaseOpr, ClearCacheDataDialog.CACHE_MODE.SWL_QSO).show());
        binding.clearShareDataButton.setOnClickListener(v -> new Thread(() -> GeneralVariables.clearCache(requireContext())).start());
    }

    private void updateControlModeUI() {
        switch (GeneralVariables.controlMode) {
            case ControlMode.VOX: binding.voxRadioButton.setChecked(true); break;
            case ControlMode.CAT: binding.catRadioButton.setChecked(true); break;
            case ControlMode.RTS: binding.rtsRadioButton.setChecked(true); break;
            case ControlMode.DTR: binding.dtrRadioButton.setChecked(true); break;
        }
        binding.controlRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.voxRadioButton) GeneralVariables.controlMode = ControlMode.VOX;
            else if (checkedId == R.id.catRadioButton) GeneralVariables.controlMode = ControlMode.CAT;
            else if (checkedId == R.id.rtsRadioButton) GeneralVariables.controlMode = ControlMode.RTS;
            else if (checkedId == R.id.dtrRadioButton) GeneralVariables.controlMode = ControlMode.DTR;
            writeConfig("ctrMode", String.valueOf(GeneralVariables.controlMode));
        });
    }

    private boolean isCatMode() {
        return GeneralVariables.controlMode == ControlMode.CAT || GeneralVariables.controlMode == ControlMode.RTS || GeneralVariables.controlMode == ControlMode.DTR;
    }

    private void setHelpDialog() {
        binding.aboutButton.setOnClickListener(v -> new HelpDialog(requireContext(), requireActivity(), "readme.txt", true).show());
        binding.faqButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), FAQActivity.class)));
    }

    private void writeConfig(String key, String value) {
        mainViewModel.databaseOpr.writeConfig(key, value, null);
    }

    private static abstract class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}

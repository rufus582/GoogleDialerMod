package com.jacopomii.googledialermod;

import static com.jacopomii.googledialermod.Utils.revertAllMods;
import static com.jacopomii.googledialermod.Utils.runSuWithCmd;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

public class SuggestedModsFragment extends Fragment {
    private View mView;
    private Switch mForceEnableCallRecordingSwitch;
    private Switch mSilenceCallRecordingAlertsSwitch;
    private DBFlagsSingleton mDBFlagsSingleton;
    private final String[] ENABLE_CALL_RECORDING_FLAGS = {
            // The following flags decide if call recording is permitted (however applying country-related restrictions)
            "G__enable_call_recording",
            "CallRecording__enable_call_recording_for_fi",
            // The following flags decide whether to bypass country-related restrictions
            "G__force_within_call_recording_geofence_value",
            "G__use_call_recording_geofence_overrides",
            "G__force_within_crosby_geofence_value"
    };
    private final String[] SILENCE_CALL_RECORDING_ALERTS_FLAGS = {
            // The following flags contain a serialized and base64 encoded list of countries in which the use of embedded audio or audio generated with TTS is forced
            // If their hashsets are all empty, dialer will use by default the TTS to generate audio call recording alerts
            "CallRecording__call_recording_countries_with_built_in_audio_file",
            "CallRecording__call_recording_force_enable_built_in_audio_file_countries",
            "CallRecording__call_recording_force_enable_tts_countries",
            // The following flags contain a serialized and base64 hashset encoded with country-language matches to be used to generate audio call recording alerts via TTS
            // If their hashsets are empty, TTS will use the default language (hardcoded in the dialer source) en_US
            "CallRecording__call_recording_countries",
            "CallRecording__crosby_countries"
    };

    public SuggestedModsFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.suggested_mods_fragment, container, false);

        mDBFlagsSingleton = DBFlagsSingleton.getInstance(requireActivity());

        mForceEnableCallRecordingSwitch = mView.findViewById(R.id.force_enable_call_recording_switch);
        mSilenceCallRecordingAlertsSwitch = mView.findViewById(R.id.silence_call_recording_alerts_switch);

        refreshSwitchesStatus();

        mForceEnableCallRecordingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (String flag : ENABLE_CALL_RECORDING_FLAGS) {
                mDBFlagsSingleton.updateDBFlag(flag, isChecked);
            }
        });

        mSilenceCallRecordingAlertsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                for (String flag : SILENCE_CALL_RECORDING_ALERTS_FLAGS) {
                    mDBFlagsSingleton.updateDBFlag(flag, "");
                }
                try {
                    final String dataDir = getActivity().getApplicationInfo().dataDir;
                    final int uid = getContext().getPackageManager().getApplicationInfo("com.google.android.dialer", 0).uid;
                    runSuWithCmd("rm -r /data/data/com.google.android.dialer/files/callrecordingprompt; " +
                            "mkdir /data/data/com.google.android.dialer/files/callrecordingprompt; " +
                            "cp " + dataDir + "/silent_wav.wav /data/data/com.google.android.dialer/files/callrecordingprompt/starting_voice-en_US.wav; " +
                            "cp " + dataDir + "/silent_wav.wav /data/data/com.google.android.dialer/files/callrecordingprompt/ending_voice-en_US.wav; " +
                            "chown -R " + uid + ":" + uid + " /data/data/com.google.android.dialer/files/callrecordingprompt; " +
                            "chmod -R 777 /data/data/com.google.android.dialer/files/callrecordingprompt; " +
                            "restorecon -R /data/data/com.google.android.dialer/files/callrecordingprompt");
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
            } else {
                mDBFlagsSingleton.deleteFlagOverrides(SILENCE_CALL_RECORDING_ALERTS_FLAGS);
                runSuWithCmd("rm -r /data/data/com.google.android.dialer/files/callrecordingprompt");
            }
        });

        return mView;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.suggested_mods_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_info_icon) {
            AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
            builder.setPositiveButton(android.R.string.ok, null)
                    .setView(R.layout.information_dialog);
            AlertDialog alert = builder.create();
            alert.show();

            // Links aren't clickable workaround
            ((TextView) alert.findViewById(R.id.what_is_it_explanation)).setMovementMethod(LinkMovementMethod.getInstance());
            ((TextView) alert.findViewById(R.id.made_with_love_by_jacopo_tediosi)).setMovementMethod(LinkMovementMethod.getInstance());

            return true;
        } else if (item.getItemId() == R.id.menu_delete_icon) {
            AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
            builder.setMessage(R.string.delete_all_mods_alert)
                    .setNegativeButton(getString(android.R.string.cancel), (dialog, which) -> {
                    })
                    .setPositiveButton(getString(android.R.string.ok), (dialog, which) -> {
                        revertAllMods(getContext());
                        refreshSwitchesStatus();
                    });
            AlertDialog alert = builder.create();
            alert.show();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    public void refreshSwitchesStatus() {
        mForceEnableCallRecordingSwitch.setChecked(
                mDBFlagsSingleton.areAllBooleanFlagsTrue(ENABLE_CALL_RECORDING_FLAGS)
        );

        int startingVoiceSize = -1;
        try {
            startingVoiceSize = Integer.parseInt(runSuWithCmd("stat -c%s /data/data/com.google.android.dialer/files/callrecordingprompt/starting_voice-en_US.wav").getInputStreamLog());
        } catch (NumberFormatException e1) {
            try {
                // Fallback if stat is not a command
                startingVoiceSize = Integer.parseInt(runSuWithCmd("ls -lS starting_voice-en_US.wav | awk '{print $5}'").getInputStreamLog());
            } catch (NumberFormatException e2) {}
        }
        mSilenceCallRecordingAlertsSwitch.setChecked(
                mDBFlagsSingleton.areAllStringFlagsEmpty(SILENCE_CALL_RECORDING_ALERTS_FLAGS) &&
                startingVoiceSize > 0 && startingVoiceSize <= 100
        );
    }
}
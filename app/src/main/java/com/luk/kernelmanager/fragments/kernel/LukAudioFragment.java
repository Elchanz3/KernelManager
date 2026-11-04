package com.luk.kernelmanager.fragments.kernel;

import com.luk.kernelmanager.R;
import com.luk.kernelmanager.fragments.ApplyOnBootFragment;
import com.luk.kernelmanager.fragments.DescriptionFragment;
import com.luk.kernelmanager.fragments.recyclerview.RecyclerViewFragment;
import com.luk.kernelmanager.utils.kernel.soc.SocNode;
import com.luk.kernelmanager.views.recyclerview.CardView;
import com.luk.kernelmanager.views.recyclerview.RecyclerViewItem;
import com.luk.kernelmanager.views.recyclerview.SeekBarView;
import com.luk.kernelmanager.views.recyclerview.SwitchView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LukAudioFragment extends RecyclerViewFragment {

    private static final String BASE = "/sys/kernel/luk_audio/";
    private static final String PITCH = BASE + "speaker/pitch_ppm";
    private static final String MIC = BASE + "mic/luk/gain_offset_halfdb";

    /* measured usable range on N975F: below/above it the amplifiers drop out */
    private static final int PITCH_MIN_PCT = -24;
    private static final int PITCH_MAX_PCT = 21;

    public static boolean supported() {
        return SocNode.exists(PITCH);
    }

    @Override
    protected void init() {
        super.init();
        addViewPagerFragment(ApplyOnBootFragment.newInstance(this));
        addViewPagerFragment(DescriptionFragment.newInstance(getString(R.string.luk_audio),
                getString(R.string.luk_audio_summary)));
    }

    @Override
    protected void addItems(List<RecyclerViewItem> items) {
        pitchInit(items);
        ampInit(items, getString(R.string.luk_amp_speaker), BASE + "amp_right/luk/");
        ampInit(items, getString(R.string.luk_amp_receiver), BASE + "amp_left/luk/");
        micInit(items);
    }

    private void write(String path, String value) {
        SocNode.write(path, value, ApplyOnBootFragment.LUK_AUDIO, getActivity());
    }

    private void pitchInit(List<RecyclerViewItem> items) {
        CardView card = new CardView(getActivity());
        card.setTitle(getString(R.string.luk_pitch));

        List<String> labels = new ArrayList<>();
        for (int pct = PITCH_MIN_PCT; pct <= PITCH_MAX_PCT; pct++) {
            double semitones = 12 * Math.log(1 + pct / 100.0) / Math.log(2);
            labels.add(String.format(Locale.US, "%+d%% (%+.1f st)", pct, semitones));
        }

        int ppm = SocNode.readInt(PITCH, 0);
        int pct = Math.round(ppm / 10000f);
        pct = Math.max(PITCH_MIN_PCT, Math.min(PITCH_MAX_PCT, pct));

        SeekBarView pitch = new SeekBarView();
        pitch.setTitle(getString(R.string.luk_pitch));
        pitch.setSummary(getString(R.string.luk_pitch_summary));
        pitch.setItems(labels);
        pitch.setProgress(pct - PITCH_MIN_PCT);
        pitch.setOnSeekBarListener(new SeekBarView.OnSeekBarListener() {
            @Override
            public void onStop(SeekBarView seekBarView, int position, String value) {
                write(PITCH, String.valueOf((position + PITCH_MIN_PCT) * 10000));
            }

            @Override
            public void onMove(SeekBarView seekBarView, int position, String value) {
            }
        });
        card.addItem(pitch);
        items.add(card);
    }

    private SeekBarView rangeSeek(String title, String summary, String path, int min, int max,
                                  int step, String unit, int current) {
        List<String> labels = new ArrayList<>();
        for (int v = min; v <= max; v += step) {
            labels.add(unit.equals(" dB/2")
                    ? String.format(Locale.US, "%+.1f dB", v / 2f)
                    : String.format(Locale.US, "%+d%s", v, unit));
        }

        SeekBarView seek = new SeekBarView();
        seek.setTitle(title);
        if (summary != null) seek.setSummary(summary);
        seek.setItems(labels);
        int clamped = Math.max(min, Math.min(max, current));
        seek.setProgress((clamped - min) / step);
        seek.setOnSeekBarListener(new SeekBarView.OnSeekBarListener() {
            @Override
            public void onStop(SeekBarView seekBarView, int position, String value) {
                write(path, String.valueOf(min + position * step));
            }

            @Override
            public void onMove(SeekBarView seekBarView, int position, String value) {
            }
        });
        return seek;
    }

    private void ampInit(List<RecyclerViewItem> items, String name, String dir) {
        if (!SocNode.exists(dir + "gain_offset_db")) return;

        CardView card = new CardView(getActivity());
        card.setTitle(name);

        card.addItem(rangeSeek(getString(R.string.luk_amp_gain),
                getString(R.string.luk_amp_gain_summary), dir + "gain_offset_db",
                -20, 2, 1, " dB", SocNode.readInt(dir + "gain_offset_db", 0)));

        card.addItem(rangeSeek(getString(R.string.luk_amp_digital),
                getString(R.string.luk_amp_digital_summary), dir + "digital_offset_db",
                -40, 12, 1, " dB", SocNode.readInt(dir + "digital_offset_db", 0)));

        SwitchView fixed = new SwitchView();
        fixed.setTitle(getString(R.string.luk_boost_fixed));
        fixed.setSummary(getString(R.string.luk_boost_fixed_summary));
        fixed.setChecked(SocNode.readInt(dir + "boost_mode", 0) == 1);
        fixed.addOnSwitchListener((switchView, isChecked) ->
                write(dir + "boost_mode", isChecked ? "1" : "0"));
        card.addItem(fixed);

        List<String> headroom = new ArrayList<>();
        for (int v = 0; v <= 63; v++) headroom.add(String.valueOf(v));
        SeekBarView hdrm = new SeekBarView();
        hdrm.setTitle(getString(R.string.luk_classh_headroom));
        hdrm.setSummary(getString(R.string.luk_classh_headroom_summary));
        hdrm.setItems(headroom);
        hdrm.setProgress(Math.max(0, Math.min(63,
                SocNode.readInt(dir + "classh_headroom", 11))));
        hdrm.setOnSeekBarListener(new SeekBarView.OnSeekBarListener() {
            @Override
            public void onStop(SeekBarView seekBarView, int position, String value) {
                write(dir + "classh_headroom", String.valueOf(position));
            }

            @Override
            public void onMove(SeekBarView seekBarView, int position, String value) {
            }
        });
        card.addItem(hdrm);

        items.add(card);
    }

    private void micInit(List<RecyclerViewItem> items) {
        if (!SocNode.exists(MIC)) return;

        CardView card = new CardView(getActivity());
        card.setTitle(getString(R.string.luk_mic));
        card.addItem(rangeSeek(getString(R.string.luk_mic_gain),
                getString(R.string.luk_mic_gain_summary), MIC,
                -40, 40, 1, " dB/2", SocNode.readInt(MIC, 0)));
        items.add(card);
    }
}

package com.luk.kernelmanager.fragments.kernel;

import android.text.InputType;

import com.luk.kernelmanager.R;
import com.luk.kernelmanager.fragments.ApplyOnBootFragment;
import com.luk.kernelmanager.fragments.DescriptionFragment;
import com.luk.kernelmanager.fragments.recyclerview.RecyclerViewFragment;
import com.luk.kernelmanager.utils.kernel.soc.SocNode;
import com.luk.kernelmanager.views.recyclerview.CardView;
import com.luk.kernelmanager.views.recyclerview.DescriptionView;
import com.luk.kernelmanager.views.recyclerview.GenericSelectView;
import com.luk.kernelmanager.views.recyclerview.RecyclerViewItem;
import com.luk.kernelmanager.views.recyclerview.SelectView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exynos devfreq bus domains (MIF, INT, DISP, …). min_freq/max_freq are
 * read-only on this driver, so limits are PM-QoS requests:
 * scaling_devfreq_min (minimum) and exynos_data/debug_scaling_devfreq_max
 * (maximum, only where the domain has a max class).
 */
public class BusFreqFragment extends RecyclerViewFragment {

    private static final String BASE = "/sys/class/devfreq/";
    private static final String[][] DOMAINS = {
            {"MIF", "17000010.devfreq_mif"},
            {"INT", "17000020.devfreq_int"},
            {"INTCAM", "17000030.devfreq_intcam"},
            {"DISP", "17000040.devfreq_disp"},
            {"CAM", "17000050.devfreq_cam"},
            {"AUD", "17000060.devfreq_aud"},
            {"IVA", "17000070.devfreq_iva"},
            {"SCORE", "17000080.devfreq_score"},
            {"MFC", "17000090.devfreq_mfc"},
            {"NPU", "170000a0.devfreq_npu"},
    };

    private final Map<String, DescriptionView> mCurrent = new LinkedHashMap<>();

    public static boolean supported() {
        for (String[] domain : DOMAINS) {
            if (SocNode.exists(BASE + domain[1] + "/scaling_devfreq_min")) return true;
        }
        return false;
    }

    @Override
    protected void init() {
        super.init();
        addViewPagerFragment(ApplyOnBootFragment.newInstance(this));
        addViewPagerFragment(DescriptionFragment.newInstance(getString(R.string.bus_freq),
                getString(R.string.bus_freq_summary)));
    }

    @Override
    protected void addItems(List<RecyclerViewItem> items) {
        mCurrent.clear();
        for (String[] domain : DOMAINS) {
            String dir = BASE + domain[1];
            if (!SocNode.exists(dir + "/scaling_devfreq_min")) continue;
            domainInit(items, domain[0], dir);
        }
    }

    private void domainInit(List<RecyclerViewItem> items, String name, String dir) {
        List<Integer> freqs = SocNode.readIntList(dir + "/available_frequencies");
        if (freqs.isEmpty()) return;
        List<String> labels = new ArrayList<>();
        for (int freq : freqs) labels.add(SocNode.mhz(freq));

        CardView card = new CardView(getActivity());
        card.setTitle(name);

        DescriptionView current = new DescriptionView();
        current.setTitle(getString(R.string.soc_current));
        current.setSummary(SocNode.mhz(SocNode.readInt(dir + "/cur_freq", 0)));
        card.addItem(current);
        mCurrent.put(dir + "/cur_freq", current);

        card.addItem(freqSelect(getString(R.string.soc_min_freq), dir + "/scaling_devfreq_min",
                freqs, labels));

        String maxPath = dir + "/exynos_data/debug_scaling_devfreq_max";
        if (!SocNode.read(maxPath).isEmpty()) {
            card.addItem(freqSelect(getString(R.string.soc_max_freq), maxPath, freqs, labels));
        }

        String tunables = dir + "/interactive/";
        String targetLoad = SocNode.read(tunables + "target_load");
        if (!targetLoad.isEmpty()) {
            GenericSelectView target = new GenericSelectView();
            target.setTitle(getString(R.string.soc_target_load));
            target.setSummary(getString(R.string.soc_target_load_summary));
            target.setValue(targetLoad);
            target.setValueRaw(targetLoad);
            target.setOnGenericValueListener((view, value) -> {
                SocNode.write(tunables + "target_load", value.trim(), ApplyOnBootFragment.BUS_FREQ,
                        getActivity());
                view.setValue(value.trim());
            });
            card.addItem(target);

            card.addItem(numberInput(getString(R.string.soc_hold_sample_time),
                    tunables + "hold_sample_time"));
        }

        items.add(card);
    }

    private SelectView freqSelect(String title, String path, List<Integer> freqs,
                                  List<String> labels) {
        SelectView select = new SelectView();
        select.setTitle(title);
        select.setItems(labels);
        int current = SocNode.readInt(path, -1);
        int index = freqs.indexOf(current);
        if (index >= 0) select.setItem(index);
        else if (current > 0) select.setSummary(SocNode.mhz(current));
        select.setOnItemSelected((view, position, item) -> SocNode.write(path,
                String.valueOf(freqs.get(position)), ApplyOnBootFragment.BUS_FREQ, getActivity()));
        return select;
    }

    private GenericSelectView numberInput(String title, String path) {
        GenericSelectView input = new GenericSelectView();
        input.setTitle(title);
        String value = SocNode.read(path);
        input.setValue(value);
        input.setValueRaw(value);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setOnGenericValueListener((view, newValue) -> {
            SocNode.write(path, newValue.trim(), ApplyOnBootFragment.BUS_FREQ, getActivity());
            view.setValue(newValue.trim());
        });
        return input;
    }

    @Override
    protected void refresh() {
        super.refresh();
        for (Map.Entry<String, DescriptionView> entry : mCurrent.entrySet()) {
            entry.getValue().setSummary(SocNode.mhz(SocNode.readInt(entry.getKey(), 0)));
        }
    }
}

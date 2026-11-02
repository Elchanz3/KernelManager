package com.luk.kernelmanager.fragments.kernel;

import android.text.InputType;

import com.luk.kernelmanager.R;
import com.luk.kernelmanager.fragments.ApplyOnBootFragment;
import com.luk.kernelmanager.fragments.DescriptionFragment;
import com.luk.kernelmanager.fragments.recyclerview.RecyclerViewFragment;
import com.luk.kernelmanager.utils.kernel.soc.SocNode;
import com.luk.kernelmanager.views.recyclerview.CardView;
import com.luk.kernelmanager.views.recyclerview.GenericSelectView;
import com.luk.kernelmanager.views.recyclerview.RecyclerViewItem;
import com.luk.kernelmanager.views.recyclerview.SeekBarView;
import com.luk.kernelmanager.views.recyclerview.SwitchView;

import java.util.ArrayList;
import java.util.List;

/**
 * Exynos Mobile Scheduler (EMS), schedtune groups, CPU idle power modes and the
 * cpuhp online mask. Formats follow the kernel store handlers:
 * ontime boundaries clamp at 1024 and coverage_ratio at 100, schedtune boost is
 * 0..100, cpupm modes take 0/1, set_online_cpu takes a hex mask that must keep
 * cpu0.
 */
public class SchedulerFragment extends RecyclerViewFragment {

    private static final String EMS = "/sys/kernel/ems";
    private static final String STUNE = "/dev/stune/";
    private static final String[] STUNE_GROUPS = {"top-app", "foreground", "background", "rt"};
    private static final String CPUPM = "/sys/power/cpupm/";
    private static final String CPUHP = "/sys/power/cpuhp/";
    private static final String CPU = "/sys/devices/system/cpu/cpu";
    private static final int NR_CPUS = 8;

    public static boolean supported() {
        return SocNode.exists(EMS) || SocNode.exists(STUNE + "top-app/schedtune.boost")
                || SocNode.exists(CPUPM) || SocNode.exists(CPUHP + "set_online_cpu");
    }

    @Override
    protected void init() {
        super.init();
        addViewPagerFragment(ApplyOnBootFragment.newInstance(this));
        addViewPagerFragment(DescriptionFragment.newInstance(getString(R.string.ems_scheduler),
                getString(R.string.ems_scheduler_summary)));
    }

    @Override
    protected void addItems(List<RecyclerViewItem> items) {
        if (SocNode.exists(EMS)) emsInit(items);
        for (String group : STUNE_GROUPS) {
            if (SocNode.exists(STUNE + group + "/schedtune.boost")) stuneInit(items, group);
        }
        idleInit(items);
        if (SocNode.exists(CPUHP + "set_online_cpu")) coresInit(items);
    }

    private void emsInit(List<RecyclerViewItem> items) {
        CardView card = new CardView(getActivity());
        card.setTitle(getString(R.string.soc_ems));
        if (SocNode.exists(EMS + "/eff_mode")) {
            card.addItem(toggle(getString(R.string.soc_eff_mode),
                    getString(R.string.soc_eff_mode_summary), EMS + "/eff_mode",
                    SocNode.readInt(EMS + "/eff_mode", 0) != 0));
        }
        items.add(card);

        for (int group = 0; group < 3; group++) {
            String dir = EMS + "/ontime/coregroup" + group + "/";
            if (!SocNode.exists(dir + "upper_boundary")) continue;
            CardView ontime = new CardView(getActivity());
            ontime.setTitle(getString(R.string.soc_ontime, group));
            ontime.addItem(number(getString(R.string.soc_upper_boundary), dir + "upper_boundary", 1024));
            ontime.addItem(number(getString(R.string.soc_lower_boundary), dir + "lower_boundary", 1024));
            ontime.addItem(number(getString(R.string.soc_upper_boundary_s), dir + "upper_boundary_s", 1024));
            ontime.addItem(number(getString(R.string.soc_lower_boundary_s), dir + "lower_boundary_s", 1024));
            ontime.addItem(number(getString(R.string.soc_coverage_ratio), dir + "coverage_ratio", 100));
            items.add(ontime);
        }
    }

    private void stuneInit(List<RecyclerViewItem> items, String group) {
        String dir = STUNE + group + "/schedtune.";
        CardView card = new CardView(getActivity());
        card.setTitle(getString(R.string.soc_schedtune, group));

        SeekBarView boost = new SeekBarView();
        boost.setTitle(getString(R.string.soc_boost));
        boost.setSummary(getString(R.string.soc_system_override));
        boost.setUnit("%");
        boost.setMax(100);
        boost.setProgress(SocNode.readInt(dir + "boost", 0));
        boost.setOnSeekBarListener(new SeekBarView.OnSeekBarListener() {
            @Override
            public void onStop(SeekBarView seekBarView, int position, String value) {
                SocNode.write(dir + "boost", String.valueOf(position),
                        ApplyOnBootFragment.SCHED, getActivity());
            }

            @Override
            public void onMove(SeekBarView seekBarView, int position, String value) {
            }
        });
        card.addItem(boost);

        String[][] flags = {
                {"prefer_idle", getString(R.string.soc_prefer_idle)},
                {"prefer_perf", getString(R.string.soc_prefer_perf)},
                {"util_est_en", getString(R.string.soc_util_est)},
                {"ontime_en", getString(R.string.soc_ontime_en)},
        };
        for (String[] flag : flags) {
            if (!SocNode.exists(dir + flag[0])) continue;
            card.addItem(toggle(flag[1], null, dir + flag[0],
                    SocNode.readInt(dir + flag[0], 0) != 0));
        }
        items.add(card);
    }

    private void idleInit(List<RecyclerViewItem> items) {
        CardView card = new CardView(getActivity());
        card.setTitle(getString(R.string.soc_cpu_idle));

        String[][] modes = {
                {"sicd", getString(R.string.soc_sicd)},
                {"cpd_cl1", getString(R.string.soc_cpd_cl1)},
        };
        for (String[] mode : modes) {
            String path = CPUPM + mode[0];
            if (!SocNode.exists(path)) continue;
            card.addItem(toggle(mode[1], null, path, SocNode.read(path).equals("enabled")));
        }

        List<String> deepIdle = new ArrayList<>();
        for (int cpu = 0; cpu < NR_CPUS; cpu++) {
            String path = CPU + cpu + "/cpuidle/state1/disable";
            if (SocNode.exists(path)) deepIdle.add(path);
        }
        if (!deepIdle.isEmpty()) {
            SwitchView sleep = new SwitchView();
            sleep.setTitle(getString(R.string.soc_deep_idle));
            sleep.setSummary(getString(R.string.soc_deep_idle_summary));
            sleep.setChecked(SocNode.readInt(deepIdle.get(0), 0) == 0);
            sleep.addOnSwitchListener((view, isChecked) -> SocNode.writeAll(deepIdle,
                    isChecked ? "0" : "1", ApplyOnBootFragment.SCHED, "cpuidle_state1_disable",
                    getActivity()));
            card.addItem(sleep);
        }

        if (card.size() > 0) items.add(card);
    }

    private void coresInit(List<RecyclerViewItem> items) {
        CardView card = new CardView(getActivity());
        card.setTitle(getString(R.string.soc_online_cores));
        final int[] mask = {parseMask(SocNode.read(CPUHP + "set_online_cpu"))};
        for (int cpu = 1; cpu < NR_CPUS; cpu++) {
            final int bit = 1 << cpu;
            SwitchView core = new SwitchView();
            core.setTitle(getString(R.string.soc_core, cpu));
            core.setChecked((mask[0] & bit) != 0);
            core.addOnSwitchListener((view, isChecked) -> {
                mask[0] = isChecked ? (mask[0] | bit) : (mask[0] & ~bit);
                mask[0] |= 1;                       // cpu0 must stay online
                SocNode.write(CPUHP + "set_online_cpu", "0x" + Integer.toHexString(mask[0]),
                        ApplyOnBootFragment.SCHED, getActivity());
            });
            card.addItem(core);
        }
        items.add(card);
    }

    /** "set online cpu : 0xff" → 0xff; all cores when unreadable. */
    private static int parseMask(String text) {
        int at = text.indexOf("0x");
        if (at < 0) return (1 << NR_CPUS) - 1;
        try {
            return Integer.parseInt(text.substring(at + 2).trim(), 16);
        } catch (NumberFormatException e) {
            return (1 << NR_CPUS) - 1;
        }
    }

    private SwitchView toggle(String title, String summary, String path, boolean checked) {
        SwitchView view = new SwitchView();
        view.setTitle(title);
        if (summary != null) view.setSummary(summary);
        view.setChecked(checked);
        view.addOnSwitchListener((switchView, isChecked) -> SocNode.write(path,
                isChecked ? "1" : "0", ApplyOnBootFragment.SCHED, getActivity()));
        return view;
    }

    private GenericSelectView number(String title, String path, int max) {
        GenericSelectView input = new GenericSelectView();
        input.setTitle(title);
        input.setSummary("0 – " + max);
        String value = String.valueOf(SocNode.readInt(path, 0));
        input.setValue(value);
        input.setValueRaw(value);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setOnGenericValueListener((view, newValue) -> {
            int number;
            try {
                number = Math.max(0, Math.min(max, Integer.parseInt(newValue.trim())));
            } catch (NumberFormatException e) {
                return;
            }
            SocNode.write(path, String.valueOf(number), ApplyOnBootFragment.SCHED, getActivity());
            view.setValue(String.valueOf(number));
            view.setValueRaw(String.valueOf(number));
        });
        return input;
    }
}

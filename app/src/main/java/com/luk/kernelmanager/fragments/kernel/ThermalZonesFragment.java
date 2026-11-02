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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exynos TMU thermal zones. Trip temperatures are in millidegrees; the
 * power-allocator gains k_po/k_pu/k_i are fixed point (value << 10), so they are
 * shown and entered divided by 1024. Trip 0 and "hot"/"critical" trips (the
 * reset safety nets) are not exposed, and the governor policy is left alone.
 */
public class ThermalZonesFragment extends RecyclerViewFragment {

    private static final String BASE = "/sys/class/thermal/thermal_zone";
    private static final int MAX_ZONES = 16;
    private static final int MAX_TRIPS = 16;
    private static final int FRAC = 1024;

    private final Map<String, DescriptionView> mTemps = new LinkedHashMap<>();

    public static boolean supported() {
        for (int i = 0; i < MAX_ZONES; i++) {
            if (SocNode.exists(BASE + i + "/trip_point_1_temp")) return true;
        }
        return false;
    }

    @Override
    protected void init() {
        super.init();
        addViewPagerFragment(ApplyOnBootFragment.newInstance(this));
        addViewPagerFragment(DescriptionFragment.newInstance(getString(R.string.thermal_zones),
                getString(R.string.thermal_zones_summary)));
    }

    @Override
    protected void addItems(List<RecyclerViewItem> items) {
        mTemps.clear();
        for (int i = 0; i < MAX_ZONES; i++) {
            String zone = BASE + i;
            if (!SocNode.exists(zone + "/type")) break;
            if (!SocNode.exists(zone + "/trip_point_1_temp")) continue;
            zoneInit(items, zone);
        }
    }

    private void zoneInit(List<RecyclerViewItem> items, String zone) {
        String policy = SocNode.read(zone + "/policy");
        boolean ipa = policy.equals("power_allocator");

        CardView card = new CardView(getActivity());
        card.setTitle(SocNode.read(zone + "/type"));

        DescriptionView temp = new DescriptionView();
        temp.setTitle(getString(R.string.soc_temperature));
        temp.setSummary(celsius(SocNode.readInt(zone + "/temp", 0)));
        card.addItem(temp);
        mTemps.put(zone + "/temp", temp);

        DescriptionView governor = new DescriptionView();
        governor.setTitle(getString(R.string.soc_thermal_governor));
        governor.setSummary(policy);
        card.addItem(governor);

        for (int trip = 1; trip < MAX_TRIPS; trip++) {
            String type = SocNode.read(zone + "/trip_point_" + trip + "_type");
            if (type.isEmpty()) break;
            if (!type.equals("active") && !type.equals("passive")) continue;
            String role = type;
            if (ipa && trip == 1) role = getString(R.string.soc_trip_switch_on);
            else if (ipa && type.equals("passive")) role = getString(R.string.soc_trip_control);
            card.addItem(scaledInput(getString(R.string.soc_trip, trip, role),
                    zone + "/trip_point_" + trip + "_temp", 1000, "°C"));
        }

        if (ipa) {
            card.addItem(scaledInput(getString(R.string.soc_k_po), zone + "/k_po", FRAC, null));
            card.addItem(scaledInput(getString(R.string.soc_k_pu), zone + "/k_pu", FRAC, null));
            card.addItem(scaledInput(getString(R.string.soc_k_i), zone + "/k_i", FRAC, null));
            card.addItem(scaledInput(getString(R.string.soc_integral_cutoff),
                    zone + "/integral_cutoff", 1, null));
            card.addItem(scaledInput(getString(R.string.soc_sustainable_power),
                    zone + "/sustainable_power", 1, "mW"));
        }

        items.add(card);
    }

    /** Edits a node shown as raw / scale; writes value * scale. */
    private GenericSelectView scaledInput(String title, String path, int scale, String unit) {
        GenericSelectView input = new GenericSelectView();
        input.setTitle(title);
        String shown = String.valueOf(SocNode.readInt(path, 0) / scale);
        input.setValue(unit == null ? shown : shown + " " + unit);
        input.setValueRaw(shown);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setOnGenericValueListener((view, value) -> {
            int number;
            try {
                number = Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                return;
            }
            SocNode.write(path, String.valueOf(number * scale), ApplyOnBootFragment.THERMAL_ZONES,
                    getActivity());
            view.setValue(unit == null ? String.valueOf(number) : number + " " + unit);
            view.setValueRaw(String.valueOf(number));
        });
        return input;
    }

    private static String celsius(int millidegrees) {
        return (millidegrees / 1000) + " °C";
    }

    @Override
    protected void refresh() {
        super.refresh();
        for (Map.Entry<String, DescriptionView> entry : mTemps.entrySet()) {
            entry.getValue().setSummary(celsius(SocNode.readInt(entry.getKey(), 0)));
        }
    }
}

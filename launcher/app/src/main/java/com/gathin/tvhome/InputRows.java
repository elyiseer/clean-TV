package com.gathin.tvhome;

import android.content.Context;
import android.media.tv.TvInputInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The TV's passthrough inputs (HDMI ports, AV, HDMI-CEC devices) as the Inputs menu and the home_inputs
 * tiles list them, kept free of Android types so the rules run in plain JVM tests.
 *
 * An HDMI-CEC device (an Apple TV, a console) gets an input of its own whose parent is the port it sits on.
 * The device takes its port's row ("Apple TV" on "HDMI 1"), and a bare port is listed only when nothing on
 * it speaks CEC, unless keepPorts asks for the port as well.
 */
final class InputRows {
    private InputRows() { }

    /** One input as TvInputInfo reports it. */
    static final class Input {
        final String id;
        /** The port's input id for a CEC device, otherwise null. */
        final String parentId;
        /** TvInputInfo.loadLabel; null falls back to the id. */
        final String label;
        /** The name given in the TV's Settings > Inputs; null or empty when there is none. */
        final String customLabel;
        final boolean hdmi, passthrough, hidden;

        Input(String id, String parentId, String label, String customLabel, boolean hdmi, boolean passthrough,
              boolean hidden) {
            this.id = id;
            this.parentId = parentId;
            this.label = label;
            this.customLabel = customLabel;
            this.hdmi = hdmi;
            this.passthrough = passthrough;
            this.hidden = hidden;
        }
    }

    /** One row: the input it opens, its name, and the port it sits on (null when the name is the port). */
    static final class Row {
        final String inputId;
        final String name;
        final String port;
        final boolean hdmi;
        /** A CEC device rather than a port. */
        final boolean device;

        Row(String inputId, String name, String port, boolean hdmi, boolean device) {
            this.inputId = inputId;
            this.name = name;
            this.port = port;
            this.hdmi = hdmi;
            this.device = device;
        }

        /** "Apple TV · HDMI 1", "MacBook Pro · HDMI 2" or "HDMI 3", as the Inputs menu shows it. */
        String text() {
            return port == null ? name : name + " \u00B7 " + port;
        }
    }

    /** The TV's inputs as {@link #build} takes them. Only this method touches the Android types. */
    static List<Input> of(Context ctx, List<TvInputInfo> infos) {
        List<Input> out = new ArrayList<>(infos.size());
        for (TvInputInfo info : infos) {
            CharSequence label = info.loadLabel(ctx), custom = info.loadCustomLabel(ctx);
            out.add(new Input(info.getId(), info.getParentId(), label == null ? null : label.toString(),
                    custom == null ? null : custom.toString(), info.getType() == TvInputInfo.TYPE_HDMI,
                    info.isPassthroughInput(), info.isHidden(ctx)));
        }
        return out;
    }

    /** The rows in port order: HDMI ports first, then AV and the rest; a port's CEC devices on its place. */
    static List<Row> build(List<Input> inputs, boolean keepPorts) {
        List<Input> sorted = new ArrayList<>(inputs);
        Collections.sort(sorted, (a, b) -> {
            if (a.hdmi != b.hdmi) return a.hdmi ? -1 : 1;
            return labelOf(a).compareToIgnoreCase(labelOf(b));
        });
        Set<String> ids = new HashSet<>();
        for (Input in : sorted) ids.add(in.id);
        Map<String, List<Input>> devices = new LinkedHashMap<>();
        for (Input in : sorted) {
            if (!isDevice(in, ids) || !in.passthrough || in.hidden) continue;
            List<Input> onPort = devices.get(in.parentId);
            if (onPort == null) devices.put(in.parentId, onPort = new ArrayList<>());
            onPort.add(in);
        }
        List<Row> rows = new ArrayList<>();
        for (Input port : sorted) {
            // Inputs hidden in the TV's Settings > Inputs stay out; a CEC device comes with its port.
            if (!port.passthrough || port.hidden || isDevice(port, ids)) continue;
            String label = portLabel(port);
            String custom = port.customLabel;
            boolean named = custom != null && !custom.isEmpty() && !custom.equals(label);
            List<Input> onPort = devices.get(port.id);
            if (onPort != null) {
                // The device's own name, or the one given to it or to its port in Settings > Inputs.
                for (Input device : onPort) {
                    String name = device.customLabel;
                    if (name == null || name.isEmpty()) name = named ? custom : device.label;
                    boolean unnamed = name == null || name.isEmpty();
                    rows.add(new Row(device.id, unnamed ? label : name, unnamed ? null : label, port.hdmi, true));
                }
                if (!keepPorts) continue;
                // The port's own row under keepPorts is just the port: a name given to it in Settings > Inputs
                // names the box behind it, which its device rows above already show.
                rows.add(new Row(port.id, label, null, port.hdmi, false));
                continue;
            }
            // The name given in Settings > Inputs (e.g. "MacBook Pro") goes first, the port after it.
            rows.add(named ? new Row(port.id, custom, label, port.hdmi, false)
                    : new Row(port.id, label, null, port.hdmi, false));
        }
        return rows;
    }

    /** home_inputs: the same rows with the CEC devices first, the inputs that are actually watched. */
    static List<Row> devicesFirst(List<Row> rows) {
        List<Row> out = new ArrayList<>(rows.size());
        for (Row r : rows) if (r.device) out.add(r);
        for (Row r : rows) if (!r.device) out.add(r);
        return out;
    }

    /** Leaves out the rows hidden_inputs names, by input id, name or full text, ignoring case. */
    static List<Row> without(List<Row> rows, List<String> hidden) {
        if (hidden.isEmpty()) return rows;
        Set<String> names = new HashSet<>();
        for (String h : hidden) names.add(h.trim().toLowerCase(Locale.ROOT));
        List<Row> out = new ArrayList<>(rows.size());
        for (Row r : rows) {
            if (names.contains(r.inputId.toLowerCase(Locale.ROOT)) || names.contains(r.name.toLowerCase(Locale.ROOT))
                    || names.contains(r.text().toLowerCase(Locale.ROOT))) continue;
            out.add(r);
        }
        return out;
    }

    private static boolean isDevice(Input in, Set<String> ids) {
        return in.parentId != null && ids.contains(in.parentId);
    }

    private static String labelOf(Input in) {
        return in.label == null ? in.id : in.label;
    }

    /** "HDMI 2" where the TV only says "2". */
    private static String portLabel(Input in) {
        String label = labelOf(in);
        if (in.hdmi && !label.toUpperCase(Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
        return label;
    }
}

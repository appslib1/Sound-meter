package com.soundmeter.decibel.noisedetector;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * Reference of dB → typical real-world sources.
 * The row closest to the currently measured dB is highlighted with a marker.
 */
public class SoundReferenceDialog extends BottomSheetDialogFragment {

    private static final String ARG_CURRENT_DB = "current_db";
    private static final String ARG_UNIT = "unit";

    private static final Row[] ROWS = new Row[]{
            new Row(120, "Threshold of pain, Thunder"),
            new Row(110, "Rock music, Car horns"),
            new Row(100, "Blow dryer, Motorcycle"),
            new Row(90,  "Diesel truck, Power tools"),
            new Row(80,  "Busy street, Alarm clocks"),
            new Row(70,  "Busy traffic, Vacuum cleaner"),
            new Row(60,  "Normal conversation at 3 ft"),
            new Row(50,  "Quiet office, Quiet street"),
            new Row(40,  "Quiet library, Park"),
            new Row(30,  "Whisper, Quiet room"),
            new Row(20,  "Mosquito, Rustling leaves"),
            new Row(10,  "Breathing, Almost quiet"),
    };

    public static SoundReferenceDialog newInstance(double currentDb, AppPrefs.Unit unit) {
        SoundReferenceDialog d = new SoundReferenceDialog();
        Bundle args = new Bundle();
        args.putDouble(ARG_CURRENT_DB, currentDb);
        args.putString(ARG_UNIT, unit.name());
        d.setArguments(args);
        return d;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new BottomSheetDialog(requireContext(), R.style.SoundMeter_BottomSheet);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottomsheet_sound_reference, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        double currentDb = requireArguments().getDouble(ARG_CURRENT_DB, Double.NaN);
        AppPrefs.Unit unit = unitFromArgs();
        int highlightIdx = closestRowIndex(currentDb);

        RecyclerView rv = view.findViewById(R.id.referenceList);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(new Adapter(highlightIdx, unit));

        if (highlightIdx >= 0) {
            rv.scrollToPosition(highlightIdx);
        }
    }

    private AppPrefs.Unit unitFromArgs() {
        String name = requireArguments().getString(ARG_UNIT, AppPrefs.Unit.DB.name());
        try {
            return AppPrefs.Unit.valueOf(name);
        } catch (IllegalArgumentException e) {
            return AppPrefs.Unit.DB;
        }
    }

    private int closestRowIndex(double currentDb) {
        if (Double.isNaN(currentDb)) return -1;
        int best = -1;
        double bestDist = Double.POSITIVE_INFINITY;
        for (int i = 0; i < ROWS.length; i++) {
            double d = Math.abs(ROWS[i].db - currentDb);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        // If we're more than 15 dB away from every reference row, don't highlight.
        return bestDist <= 15 ? best : -1;
    }

    // ================== data ==================

    private static final class Row {
        final int db;
        final String description;
        Row(int db, String description) {
            this.db = db;
            this.description = description;
        }
    }

    // ================== adapter ==================

    private static final class Adapter extends RecyclerView.Adapter<Adapter.VH> {

        private final int highlightIdx;
        private final AppPrefs.Unit unit;

        Adapter(int highlightIdx, AppPrefs.Unit unit) {
            this.highlightIdx = highlightIdx;
            this.unit = unit;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_sound_reference, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Row r = ROWS[position];
            Context ctx = h.itemView.getContext();

            if (unit == AppPrefs.Unit.B) {
                h.dbText.setText(ctx.getString(R.string.reference_b_format, r.db / 10.0));
            } else {
                h.dbText.setText(ctx.getString(R.string.reference_db_format, r.db));
            }
            h.descText.setText(r.description);

            boolean active = position == highlightIdx;
            int color = ContextCompat.getColor(ctx,
                    active ? R.color.level_extreme : R.color.text_primary);
            h.dbText.setTextColor(color);
            h.descText.setTextColor(color);
            h.marker.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
        }

        @Override
        public int getItemCount() {
            return ROWS.length;
        }

        static final class VH extends RecyclerView.ViewHolder {
            final TextView marker;
            final TextView dbText;
            final TextView descText;
            VH(@NonNull View v) {
                super(v);
                marker = v.findViewById(R.id.rowMarker);
                dbText = v.findViewById(R.id.rowDb);
                descText = v.findViewById(R.id.rowDesc);
            }
        }
    }
}

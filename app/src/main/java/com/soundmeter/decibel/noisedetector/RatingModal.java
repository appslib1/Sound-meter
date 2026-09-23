package com.soundmeter.decibel.noisedetector;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * Rating modal ported from the "power_and_volume_btns" app.
 *
 * Key behavior: tapping a star only updates the UI (face, title, subtitle). The user must press
 * "Rate" to submit — this prevents accidental taps from launching Play Store / email.
 * 4-5 stars → Play Store. 1-3 stars → feedback email.
 */
public class RatingModal {

    private static final String FEEDBACK_EMAIL = "jawad@feelandclic.com";
    private static final String GMAIL_PACKAGE = "com.google.android.gm";

    private final Context context;
    private int selectedStars = 0;
    private final int[] starIds = {
            R.id.star1, R.id.star2, R.id.star3, R.id.star4, R.id.star5
    };

    private ImageView face;
    private TextView title;
    private TextView subtitle;
    private Button submitButton;

    public RatingModal(Context context) {
        this.context = context;
    }

    public void openRatingDialog() {
        final BottomSheetDialog dialog = new BottomSheetDialog(context);
        dialog.setContentView(R.layout.rating_modal);

        // Transparent sheet container so our rounded background shows through.
        dialog.setOnShowListener(d -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) sheet.setBackgroundColor(Color.TRANSPARENT);
        });

        face = dialog.findViewById(R.id.ratingFace);
        title = dialog.findViewById(R.id.ratingTitle);
        subtitle = dialog.findViewById(R.id.ratingSubtitle);

        final ImageButton[] starButtons = new ImageButton[5];
        for (int i = 0; i < 5; i++) {
            final int index = i;
            starButtons[i] = dialog.findViewById(starIds[i]);
            starButtons[i].setOnClickListener(v -> updateStarUI(starButtons, index + 1));
        }

        ImageButton closeButton = dialog.findViewById(R.id.closeRatingButton);
        if (closeButton != null) closeButton.setOnClickListener(v -> dialog.dismiss());

        submitButton = dialog.findViewById(R.id.submitRatingButton);
        submitButton.setOnClickListener(v -> {
            if (selectedStars >= 4) {
                openPlayStore();
            } else {
                openEmailIntent();
            }
            dialog.dismiss();
        });

        updateStarUI(starButtons, selectedStars);
        dialog.show();
    }

    private void updateStarUI(ImageButton[] buttons, int count) {
        selectedStars = count;

        int filledColor = ContextCompat.getColor(context,
                count >= 4 ? R.color.rating_amber : R.color.rating_red);
        int emptyColor = ContextCompat.getColor(context, R.color.rating_star_empty);

        for (int i = 0; i < buttons.length; i++) {
            boolean filled = i < count;
            int drawableId = filled ? R.drawable.baseline_star_24 : R.drawable.baseline_star_outline_24;
            buttons[i].setImageDrawable(ContextCompat.getDrawable(context, drawableId));
            buttons[i].setImageTintList(ColorStateList.valueOf(filled ? filledColor : emptyColor));
        }

        updateMoodUI(count);
    }

    private void updateMoodUI(int count) {
        if (count == 0) {
            face.setImageResource(R.drawable.ic_face_star);
            title.setText(R.string.rate_title_idle);
            subtitle.setVisibility(View.GONE);
        } else if (count >= 4) {
            face.setImageResource(count == 5 ? R.drawable.ic_face_love : R.drawable.ic_face_happy);
            title.setText(R.string.rate_title_high);
            subtitle.setText(R.string.rate_subtitle_high);
            subtitle.setVisibility(View.VISIBLE);
        } else {
            face.setImageResource(count == 3 ? R.drawable.ic_face_neutral : R.drawable.ic_face_sad);
            title.setText(R.string.rate_title_low);
            subtitle.setText(R.string.rate_subtitle_low);
            subtitle.setVisibility(View.VISIBLE);
        }
        submitButton.setEnabled(count > 0);
    }

    private void openPlayStore() {
        String pkg = context.getPackageName();
        try {
            Intent i = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            context.startActivity(i);
        } catch (Exception e) {
            context.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
        }
    }

    private void openEmailIntent() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("message/rfc822");
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{FEEDBACK_EMAIL});
        intent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.rate_feedback_subject));

        // Try Gmail first, fall back to chooser if not installed.
        intent.setPackage(GMAIL_PACKAGE);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            intent.setPackage(null);
            try {
                context.startActivity(Intent.createChooser(intent,
                        context.getString(R.string.rate_share_via)));
            } catch (Exception ex) {
                Toast.makeText(context, R.string.rate_no_email, Toast.LENGTH_SHORT).show();
            }
        }
    }
}

/*
 * Copyright (C) 2026 Alch3myOS
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Makes the launcher's settings pages follow the phone-wide Settings style (Stock, Minimal,
 * Aurora or Alch3mist), when the "Follow Settings style" switch is on. It reads the same
 * {@code alch3my_settings_style} key the Settings app and ArcaniumUI use, and restyles each
 * preference row's icon (and, for Minimal, flattens the card) so the launcher's sub-menus line
 * up with the rest of the system instead of the stock launcher look.
 *
 * <p>It keeps the launcher's own list adapter and card grouping untouched - it only decorates
 * rows as they attach - and every step is guarded so a styling miss can never crash a screen.
 */
public final class LauncherMenuStyler {

    /** The launcher SharedPreferences flag that turns the whole thing on. Off by default. */
    public static final String KEY_ENABLED = "pref_launcher_menu_style";
    /** The phone-wide Settings style, shared with the Settings app and ArcaniumUI. */
    public static final String KEY_STYLE = "alch3my_settings_style";

    public static final int STYLE_STOCK = 0;
    public static final int STYLE_MINIMAL = 1;
    public static final int STYLE_AURORA = 2;
    public static final int STYLE_ALCH3MIST = 3;

    private LauncherMenuStyler() {}

    public static boolean isEnabled(Context context) {
        if (context == null) {
            return false;
        }
        try {
            SharedPreferences prefs = context.getSharedPreferences(
                    com.android.launcher3.LauncherFiles.SHARED_PREFERENCES_KEY,
                    Context.MODE_PRIVATE);
            return prefs.getBoolean(KEY_ENABLED, false);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static int style(Context context) {
        return context == null ? STYLE_STOCK : Settings.System.getInt(
                context.getContentResolver(), KEY_STYLE, STYLE_STOCK);
    }

    /**
     * Attaches the styler to a settings list. A launcher settings fragment calls this from
     * {@code onCreateRecyclerView}. Does nothing unless the switch is on and a non-Stock style is
     * chosen, so the launcher keeps its own look otherwise.
     */
    public static void attach(RecyclerView list, Context context) {
        if (list == null || !isEnabled(context)) {
            return;
        }
        final int style = style(context);
        if (style == STYLE_STOCK) {
            return;
        }
        list.addOnChildAttachStateChangeListener(
                new RecyclerView.OnChildAttachStateChangeListener() {
                    @Override
                    public void onChildViewAttachedToWindow(View view) {
                        styleRowSafely(view, style);
                    }

                    @Override
                    public void onChildViewDetachedFromWindow(View view) {}
                });
        // Rows already laid out when the listener is added (the first screenful).
        list.post(() -> {
            for (int i = 0; i < list.getChildCount(); i++) {
                styleRowSafely(list.getChildAt(i), style);
            }
        });
    }

    private static void styleRowSafely(View row, int style) {
        try {
            styleRow(row, style);
        } catch (RuntimeException ignored) {
            // Never let a styling miss break the list.
        }
    }

    /** Styles one row's card fill and icon for the chosen style. */
    private static void styleRow(View row, int style) {
        if (row == null) {
            return;
        }
        Context ctx = row.getContext();
        int accent = resolve(ctx, android.R.attr.colorAccent, 0xFF3367D6);
        int textPrimary = resolve(ctx, android.R.attr.textColorPrimary, 0xFFFFFFFF);

        // Minimal flattens the launcher's own cards; the other styles keep them.
        if (style == STYLE_MINIMAL) {
            row.setBackground(null);
        }

        View iconView = row.findViewById(android.R.id.icon);
        if (iconView instanceof ImageView && ((ImageView) iconView).getDrawable() != null) {
            styleIcon((ImageView) iconView, style, accent, textPrimary);
        }
    }

    /** The icon tile and glyph colour for a style (always contrasting with its own tile). */
    private static void styleIcon(ImageView icon, int style, int accent, int textPrimary) {
        int size = dp(icon, 40);
        ViewGroup.LayoutParams lp = icon.getLayoutParams();
        if (lp != null && (lp.width != size || lp.height != size)) {
            lp.width = size;
            lp.height = size;
            icon.setLayoutParams(lp);
        }
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad;
        switch (style) {
            case STYLE_MINIMAL:
                icon.setBackground(null);
                icon.setPadding(0, 0, 0, 0);
                icon.setImageTintList(ColorStateList.valueOf(textPrimary));
                break;
            case STYLE_AURORA: {
                GradientDrawable tile = new GradientDrawable();
                tile.setShape(GradientDrawable.OVAL);
                tile.setColor((accent & 0x00FFFFFF) | 0x2E000000);
                tile.setStroke(dp(icon, 2), accent);
                icon.setBackground(tile);
                pad = dp(icon, 9);
                icon.setPadding(pad, pad, pad, pad);
                icon.setImageTintList(ColorStateList.valueOf(accent));
                break;
            }
            case STYLE_ALCH3MIST:
            default: {
                GradientDrawable tile = new GradientDrawable();
                tile.setShape(GradientDrawable.RECTANGLE);
                tile.setCornerRadius(size * 0.30f);
                tile.setColor(accent);
                icon.setBackground(tile);
                pad = dp(icon, 9);
                icon.setPadding(pad, pad, pad, pad);
                icon.setImageTintList(ColorStateList.valueOf(onColor(accent)));
                break;
            }
        }
    }

    /** Black or white, whichever reads on the colour. */
    private static int onColor(int color) {
        return Color.luminance(color) > 0.4f ? 0xFF000000 : 0xFFFFFFFF;
    }

    private static int resolve(Context ctx, int attr, int fallback) {
        try {
            TypedValue tv = new TypedValue();
            if (ctx.getTheme().resolveAttribute(attr, tv, true)) {
                return tv.resourceId != 0 ? ctx.getColor(tv.resourceId) : tv.data;
            }
        } catch (RuntimeException ignored) {
        }
        return fallback;
    }

    private static int dp(View v, int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                v.getResources().getDisplayMetrics()));
    }
}

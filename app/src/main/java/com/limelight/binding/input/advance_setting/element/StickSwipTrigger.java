package com.limelight.binding.input.advance_setting.element;

import android.content.ContentValues;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.limelight.binding.input.advance_setting.PageDeviceController;
import com.limelight.binding.input.advance_setting.superpage.NumberSeekbar;

import java.util.Map;

final class StickSwipTrigger {
    static final String ATTR_VALUE = "stickSpecialValue";
    static final String ATTR_TRIGGER_RADIUS_PERCENT = "stickSpecialTriggerRadiusPercent";
    static final String ATTR_STICK_PRESS_VIBRATION_ENABLED = "stickPressVibrationEnabled";

    private static final String DEFAULT_VALUE = "null";
    private static final int MIN_RADIUS_PERCENT = 100;
    private static final int MAX_RADIUS_PERCENT = 300;
    private static final int DEFAULT_TRIGGER_RADIUS_PERCENT = MIN_RADIUS_PERCENT;
    private static final boolean DEFAULT_STICK_PRESS_VIBRATION_ENABLED = false;
    private static final int PREVIEW_COLOR = 0xFFFF9800;

    private final ElementController elementController;
    private final JsonObject extraAttributes;
    private final Paint previewPaint = new Paint();

    private String value = DEFAULT_VALUE;
    private int triggerRadiusPercent = DEFAULT_TRIGGER_RADIUS_PERCENT;
    private boolean stickPressVibrationEnabled = DEFAULT_STICK_PRESS_VIBRATION_ENABLED;
    private ElementController.SendEventHandler sendHandler;
    private boolean pressed;
    private boolean previewing;
    private int previewRadiusPercent = DEFAULT_TRIGGER_RADIUS_PERCENT;

    StickSwipTrigger(Map<String, Object> attributesMap, ElementController elementController) {
        this.elementController = elementController;
        this.extraAttributes = parseExtraAttributes(attributesMap.get(Element.COLUMN_STRING_EXTRA_ATTRIBUTES));
        previewPaint.setStyle(Paint.Style.STROKE);
        previewPaint.setStrokeWidth(4);
        previewPaint.setColor(PREVIEW_COLOR);
        previewPaint.setPathEffect(new DashPathEffect(new float[]{10, 20}, 0));
        load();
        this.sendHandler = elementController.getSendEventHandler(value);
    }

    private JsonObject parseExtraAttributes(Object extraAttributesObj) {
        if (!(extraAttributesObj instanceof String)) {
            return new JsonObject();
        }

        String json = (String) extraAttributesObj;
        if (json == null || json.isEmpty()) {
            return new JsonObject();
        }

        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private void load() {
        if (extraAttributes.has(ATTR_VALUE)) {
            value = extraAttributes.get(ATTR_VALUE).getAsString();
        }
        triggerRadiusPercent = readRadiusPercent(ATTR_TRIGGER_RADIUS_PERCENT, DEFAULT_TRIGGER_RADIUS_PERCENT);
        stickPressVibrationEnabled = readBoolean(
                ATTR_STICK_PRESS_VIBRATION_ENABLED,
                DEFAULT_STICK_PRESS_VIBRATION_ENABLED
        );
    }

    private int readRadiusPercent(String key, int defaultValue) {
        if (!extraAttributes.has(key)) {
            return defaultValue;
        }
        try {
            return clampRadiusPercent(extraAttributes.get(key).getAsInt());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    void bind(TextView valueTextView,
              Switch stickPressVibrationSwitch,
              NumberSeekbar triggerRadiusSeekbar,
              PageDeviceController pageDeviceController,
              Runnable saveCallback,
              Runnable invalidateCallback) {
        valueTextView.setText(pageDeviceController.getKeyNameByValue(value));
        valueTextView.setOnClickListener(v -> {
            PageDeviceController.DeviceCallBack deviceCallBack = key -> {
                ((TextView) v).setText(key.getText());
                setValue(key.getTag().toString());
                saveCallback.run();
            };
            pageDeviceController.open(deviceCallBack, View.VISIBLE, View.VISIBLE, View.VISIBLE);
        });

        stickPressVibrationSwitch.setChecked(stickPressVibrationEnabled);
        stickPressVibrationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            stickPressVibrationEnabled = isChecked;
            saveCallback.run();
        });

        bindTriggerRadiusSeekbar(triggerRadiusSeekbar, saveCallback, invalidateCallback);
    }

    private boolean readBoolean(String key, boolean defaultValue) {
        if (!extraAttributes.has(key)) {
            return defaultValue;
        }
        try {
            return extraAttributes.get(key).getAsBoolean();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private void bindTriggerRadiusSeekbar(NumberSeekbar seekbar, Runnable saveCallback, Runnable invalidateCallback) {
        seekbar.setProgressMin(MIN_RADIUS_PERCENT);
        seekbar.setProgressMax(MAX_RADIUS_PERCENT);
        seekbar.setValueWithNoCallBack(triggerRadiusPercent);
        seekbar.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                previewRadiusPercent = clampRadiusPercent(progress);
                previewing = true;
                invalidateCallback.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                previewRadiusPercent = clampRadiusPercent(seekBar.getProgress());
                previewing = true;
                invalidateCallback.run();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                setTriggerRadiusPercent(seekBar.getProgress());
                previewing = false;
                saveCallback.run();
                invalidateCallback.run();
            }
        });
    }

    void drawTriggerPreview(Canvas canvas, float centerX, float centerY, float radiusComplete) {
        if (!previewing || previewRadiusPercent <= MIN_RADIUS_PERCENT) {
            return;
        }
        float previewRadius = radiusComplete * previewRadiusPercent / 100.0f;
        canvas.drawCircle(centerX, centerY, previewRadius, previewPaint);
    }

    void update(double rawMovementRadius, float radiusComplete) {
        if (triggerRadiusPercent <= MIN_RADIUS_PERCENT) {
            return;
        }

        // Once triggered in this touch session, stay pressed until release
        if (pressed) {
            return;
        }

        boolean shouldPress = rawMovementRadius >= getTriggerRadius(radiusComplete);
        if (shouldPress) {
            sendHandler.sendEvent(true);
            pressed = true;
            elementController.buttonVibrator();
        }
    }

    void onStickPressed() {
        if (stickPressVibrationEnabled) {
            elementController.buttonVibrator();
        }
    }

    void release() {
        if (!pressed) {
            return;
        }
        sendHandler.sendEvent(false);
        pressed = false;
    }

    void putExtraAttributes(ContentValues contentValues) {
        extraAttributes.addProperty(ATTR_VALUE, value);
        extraAttributes.addProperty(ATTR_TRIGGER_RADIUS_PERCENT, triggerRadiusPercent);
        extraAttributes.addProperty(ATTR_STICK_PRESS_VIBRATION_ENABLED, stickPressVibrationEnabled);
        contentValues.put(Element.COLUMN_STRING_EXTRA_ATTRIBUTES, new Gson().toJson(extraAttributes));
    }

    private double getTriggerRadius(float radiusComplete) {
        return radiusComplete * triggerRadiusPercent / 100.0;
    }

    private void setValue(String value) {
        this.value = value;
        this.sendHandler = elementController.getSendEventHandler(value);
    }

    private void setTriggerRadiusPercent(int triggerRadiusPercent) {
        this.triggerRadiusPercent = clampRadiusPercent(triggerRadiusPercent);
    }

    private int clampRadiusPercent(int value) {
        return Math.max(MIN_RADIUS_PERCENT, Math.min(MAX_RADIUS_PERCENT, value));
    }
}

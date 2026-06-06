package com.limelight.binding.input.advance_setting.element;

import android.content.ContentValues;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.limelight.binding.input.advance_setting.PageDeviceController;
import com.limelight.binding.input.advance_setting.superpage.NumberSeekbar;

import java.util.Map;

final class StickSpecialButton {
    static final String ATTR_VALUE = "stickSpecialValue";
    static final String ATTR_HIT_RADIUS_PERCENT = "stickSpecialHitRadiusPercent";
    static final String ATTR_TRIGGER_RADIUS_PERCENT = "stickSpecialTriggerRadiusPercent";

    private static final String DEFAULT_VALUE = "null";
    private static final int DEFAULT_HIT_RADIUS_PERCENT = 130;
    private static final int DEFAULT_TRIGGER_RADIUS_PERCENT = 120;
    private static final int MIN_RADIUS_PERCENT = 100;
    private static final int MAX_RADIUS_PERCENT = 300;

    private final ElementController elementController;
    private final JsonObject extraAttributes;

    private String value = DEFAULT_VALUE;
    private int hitRadiusPercent = DEFAULT_HIT_RADIUS_PERCENT;
    private int triggerRadiusPercent = DEFAULT_TRIGGER_RADIUS_PERCENT;
    private ElementController.SendEventHandler sendHandler;
    private boolean pressed;

    StickSpecialButton(Map<String, Object> attributesMap, ElementController elementController) {
        this.elementController = elementController;
        this.extraAttributes = parseExtraAttributes(attributesMap.get(Element.COLUMN_STRING_EXTRA_ATTRIBUTES));
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
        hitRadiusPercent = readRadiusPercent(ATTR_HIT_RADIUS_PERCENT, DEFAULT_HIT_RADIUS_PERCENT);
        triggerRadiusPercent = readRadiusPercent(ATTR_TRIGGER_RADIUS_PERCENT, DEFAULT_TRIGGER_RADIUS_PERCENT);
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
              NumberSeekbar hitRadiusSeekbar,
              NumberSeekbar triggerRadiusSeekbar,
              PageDeviceController pageDeviceController,
              Runnable saveCallback) {
        valueTextView.setText(pageDeviceController.getKeyNameByValue(value));
        valueTextView.setOnClickListener(v -> {
            PageDeviceController.DeviceCallBack deviceCallBack = key -> {
                ((TextView) v).setText(key.getText());
                setValue(key.getTag().toString());
                saveCallback.run();
            };
            pageDeviceController.open(deviceCallBack, View.VISIBLE, View.VISIBLE, View.VISIBLE);
        });

        bindRadiusSeekbar(hitRadiusSeekbar, hitRadiusPercent, progress -> {
            setHitRadiusPercent(progress);
            saveCallback.run();
        });
        bindRadiusSeekbar(triggerRadiusSeekbar, triggerRadiusPercent, progress -> {
            setTriggerRadiusPercent(progress);
            saveCallback.run();
        });
    }

    private void bindRadiusSeekbar(NumberSeekbar seekbar, int value, RadiusChangeListener listener) {
        seekbar.setProgressMin(MIN_RADIUS_PERCENT);
        seekbar.setProgressMax(MAX_RADIUS_PERCENT);
        seekbar.setValueWithNoCallBack(value);
        seekbar.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                listener.onChanged(seekBar.getProgress());
            }
        });
    }

    void update(double rawMovementRadius, float radiusComplete) {
        boolean shouldPress = rawMovementRadius >= getTriggerRadius(radiusComplete);
        if (shouldPress && !pressed) {
            sendHandler.sendEvent(true);
            pressed = true;
            elementController.buttonVibrator();
        } else if (!shouldPress && pressed) {
            release();
        }
    }

    void release() {
        if (!pressed) {
            return;
        }
        sendHandler.sendEvent(false);
        pressed = false;
    }

    double getHitRadius(float radiusComplete) {
        return radiusComplete * hitRadiusPercent / 100.0;
    }

    void putExtraAttributes(ContentValues contentValues) {
        extraAttributes.addProperty(ATTR_VALUE, value);
        extraAttributes.addProperty(ATTR_HIT_RADIUS_PERCENT, hitRadiusPercent);
        extraAttributes.addProperty(ATTR_TRIGGER_RADIUS_PERCENT, triggerRadiusPercent);
        contentValues.put(Element.COLUMN_STRING_EXTRA_ATTRIBUTES, new Gson().toJson(extraAttributes));
    }

    private double getTriggerRadius(float radiusComplete) {
        return radiusComplete * triggerRadiusPercent / 100.0;
    }

    private void setValue(String value) {
        this.value = value;
        this.sendHandler = elementController.getSendEventHandler(value);
    }

    private void setHitRadiusPercent(int hitRadiusPercent) {
        this.hitRadiusPercent = clampRadiusPercent(hitRadiusPercent);
    }

    private void setTriggerRadiusPercent(int triggerRadiusPercent) {
        this.triggerRadiusPercent = clampRadiusPercent(triggerRadiusPercent);
    }

    private int clampRadiusPercent(int value) {
        return Math.max(MIN_RADIUS_PERCENT, Math.min(MAX_RADIUS_PERCENT, value));
    }

    private interface RadiusChangeListener {
        void onChanged(int value);
    }
}

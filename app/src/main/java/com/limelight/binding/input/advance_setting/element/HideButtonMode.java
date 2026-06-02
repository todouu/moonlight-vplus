package com.limelight.binding.input.advance_setting.element;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.limelight.Game;
import com.limelight.R;
import com.limelight.binding.input.advance_setting.PageDeviceController;
import com.limelight.binding.input.advance_setting.superpage.ElementEditText;
import com.limelight.binding.input.advance_setting.superpage.NumberSeekbar;
import com.limelight.binding.input.advance_setting.superpage.SuperPageLayout;
import com.limelight.binding.input.advance_setting.superpage.SuperPagesController;
import com.limelight.utils.ColorPickerDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mouse Free Mode element - standalone toggle button that switches between
 * trackpad mode (free mouse) and normal mouse mode.
 * Stores a list of element IDs to hide when free mode is activated.
 */
public class HideButtonMode extends Element {

    private PageDeviceController pageDeviceController;
    private HideButtonMode hideButtonMode;
    private List<Element> hideElementList = new ArrayList<>();
    private List<Long> parsedHideIds = null;
    private Context context;

    private long lastToggleTime = 0;
    private static final long TOGGLE_DEBOUNCE_MS = 300;

    private String text;
    private String value; // comma-separated element IDs to hide
    private int hideMode; // 0 = trackpad mode, 1 = multi-touch mode (when buttons are hidden)
    private int radius;
    private int thick;
    private int normalColor;
    private int pressedColor;
    private int backgroundColor;
    private int normalTextColor;
    private int pressedTextColor;
    private int textSizePercent;

    private SuperPageLayout hideButtonModeSettingPage;
    private NumberSeekbar centralXNumberSeekbar;
    private NumberSeekbar centralYNumberSeekbar;

    private final Paint paintBorder = new Paint();
    private final Paint paintBackground = new Paint();
    private final Paint paintText = new Paint();
    private final Paint paintEdit = new Paint();
    private final RectF rect = new RectF();

    public HideButtonMode(Map<String, Object> attributesMap,
                         ElementController controller,
                         PageDeviceController pageDeviceController,
                         Context context) {
        super(attributesMap, controller, context);
        this.pageDeviceController = pageDeviceController;
        this.hideButtonMode = this;
        this.context = context;

        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((Game) context).getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
        super.centralXMax = displayMetrics.widthPixels;
        super.centralXMin = 0;
        super.centralYMax = displayMetrics.heightPixels;
        super.centralYMin = 0;
        super.widthMax = displayMetrics.widthPixels / 2;
        super.widthMin = 50;
        super.heightMax = displayMetrics.heightPixels / 2;
        super.heightMin = 50;

        paintEdit.setStyle(Paint.Style.STROKE);
        paintEdit.setStrokeWidth(4);
        paintEdit.setPathEffect(new DashPathEffect(new float[]{10, 20}, 0));
        paintText.setTextAlign(Paint.Align.CENTER);
        paintBorder.setStyle(Paint.Style.STROKE);
        paintBackground.setStyle(Paint.Style.FILL);

        text = (String) attributesMap.get(COLUMN_STRING_ELEMENT_TEXT);
        radius = ((Long) attributesMap.get(COLUMN_INT_ELEMENT_RADIUS)).intValue();
        thick = ((Long) attributesMap.get(COLUMN_INT_ELEMENT_THICK)).intValue();
        normalColor = ((Long) attributesMap.get(COLUMN_INT_ELEMENT_NORMAL_COLOR)).intValue();
        pressedColor = ((Long) attributesMap.get(COLUMN_INT_ELEMENT_PRESSED_COLOR)).intValue();
        backgroundColor = ((Long) attributesMap.get(COLUMN_INT_ELEMENT_BACKGROUND_COLOR)).intValue();
        value = (String) attributesMap.get(COLUMN_STRING_ELEMENT_VALUE);

        // Load text properties with backward compatibility
        if (attributesMap.containsKey(COLUMN_INT_ELEMENT_NORMAL_TEXT_COLOR)) {
            normalTextColor = ((Long) attributesMap.get(COLUMN_INT_ELEMENT_NORMAL_TEXT_COLOR)).intValue();
        } else {
            normalTextColor = normalColor;
        }

        if (attributesMap.containsKey(COLUMN_INT_ELEMENT_PRESSED_TEXT_COLOR)) {
            pressedTextColor = ((Long) attributesMap.get(COLUMN_INT_ELEMENT_PRESSED_TEXT_COLOR)).intValue();
        } else {
            pressedTextColor = pressedColor;
        }

        if (attributesMap.containsKey(COLUMN_INT_ELEMENT_TEXT_SIZE_PERCENT)) {
            textSizePercent = ((Long) attributesMap.get(COLUMN_INT_ELEMENT_TEXT_SIZE_PERCENT)).intValue();
        } else {
            textSizePercent = 25;
        }

        // Parse hide element IDs from value field
        parseHideIds();

        // Load hide mode (0=trackpad, 1=multi-touch)
        if (attributesMap.containsKey(COLUMN_INT_ELEMENT_MODE)) {
            hideMode = ((Long) attributesMap.get(COLUMN_INT_ELEMENT_MODE)).intValue();
        } else {
            hideMode = 0; // default: trackpad
        }
    }

    private void parseHideIds() {
        if (value != null && !value.isEmpty() && !"-1".equals(value)) {
            parsedHideIds = new ArrayList<>();
            String[] idArray = value.split(",");
            for (String id : idArray) {
                try {
                    long parsed = Long.parseLong(id.trim());
                    if (parsed != -1) {
                        parsedHideIds.add(parsed);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    /**
     * Returns the list of element IDs that should be hidden when MFM is active.
     */
    public List<Long> getHideElementIds() {
        return parsedHideIds;
    }

    /**
     * Returns the hide mode: 0 = trackpad, 1 = multi-touch.
     */
    public int getHideMode() {
        return hideMode;
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        int elementWidth = getElementWidth();
        int elementHeight = getElementHeight();

        float textSize = getPercent(elementHeight, textSizePercent);
        paintText.setTextSize(textSize);
        paintText.setColor(isPressed() ? pressedTextColor : normalTextColor);
        paintBorder.setStrokeWidth(thick);
        paintBorder.setColor(isPressed() ? pressedColor : normalColor);
        paintBackground.setColor(backgroundColor);

        float centerX = elementWidth / 2f;
        Paint.FontMetrics fontMetrics = paintText.getFontMetrics();
        float baselineY = elementHeight / 2f - (fontMetrics.top + fontMetrics.bottom) / 2f;

        rect.left = rect.top = (float) thick / 2;
        rect.right = elementWidth - rect.left;
        rect.bottom = elementHeight - rect.top;
        canvas.drawRoundRect(rect, radius, radius, paintBackground);
        canvas.drawRoundRect(rect, radius, radius, paintBorder);
        canvas.drawText(text, centerX, baselineY, paintText);

        ElementController.Mode mode = elementController.getMode();
        if (mode == ElementController.Mode.Edit || mode == ElementController.Mode.Select) {
            rect.left = rect.top = 2;
            rect.right = getWidth() - 2;
            rect.bottom = getHeight() - 2;
            paintEdit.setColor(editColor);
            canvas.drawRect(rect, paintEdit);
        }
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                long now = System.currentTimeMillis();
                if (now - lastToggleTime < TOGGLE_DEBOUNCE_MS) {
                    return true; // Ignore rapid double-tap
                }
                lastToggleTime = now;

                elementController.buttonVibrator();
                // Toggle MFM via the centralized handler
                toggleHideButtonMode();
                // Sync pressed/visual state with actual state
                setPressed(elementController.isHideButtonModeActive());
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                return true;
            }
            default: {
            }
        }
        return true;
    }

    private void toggleHideButtonMode() {
        // Delegate to the MFM handler in ElementController for consistent behavior
        ElementController.SendEventHandler handler = elementController.getSendEventHandler("MFM");
        if (handler != null) {
            handler.sendEvent(true); // The handler toggles internally
        }
    }

    @Override
    public void save() {
        ContentValues contentValues = new ContentValues();
        contentValues.put(COLUMN_STRING_ELEMENT_TEXT, text);
        contentValues.put(COLUMN_STRING_ELEMENT_VALUE, value);
        contentValues.put(COLUMN_INT_ELEMENT_WIDTH, getElementWidth());
        contentValues.put(COLUMN_INT_ELEMENT_HEIGHT, getElementHeight());
        contentValues.put(COLUMN_INT_ELEMENT_CENTRAL_X, getElementCentralX());
        contentValues.put(COLUMN_INT_ELEMENT_CENTRAL_Y, getElementCentralY());
        contentValues.put(COLUMN_INT_ELEMENT_RADIUS, radius);
        contentValues.put(COLUMN_INT_ELEMENT_THICK, thick);
        contentValues.put(COLUMN_INT_ELEMENT_LAYER, layer);
        contentValues.put(COLUMN_INT_ELEMENT_NORMAL_COLOR, normalColor);
        contentValues.put(COLUMN_INT_ELEMENT_PRESSED_COLOR, pressedColor);
        contentValues.put(COLUMN_INT_ELEMENT_BACKGROUND_COLOR, backgroundColor);
        contentValues.put(COLUMN_INT_ELEMENT_NORMAL_TEXT_COLOR, normalTextColor);
        contentValues.put(COLUMN_INT_ELEMENT_PRESSED_TEXT_COLOR, pressedTextColor);
        contentValues.put(COLUMN_INT_ELEMENT_TEXT_SIZE_PERCENT, textSizePercent);
        contentValues.put(COLUMN_INT_ELEMENT_MODE, hideMode);
        elementController.updateElement(elementId, contentValues);
    }

    @Override
    protected void updatePage() {
        if (hideButtonModeSettingPage != null) {
            centralXNumberSeekbar.setValueWithNoCallBack(getElementCentralX());
            centralYNumberSeekbar.setValueWithNoCallBack(getElementCentralY());
        }
    }

    @Override
    protected SuperPageLayout getInfoPage() {
        if (hideButtonModeSettingPage == null) {
            hideButtonModeSettingPage = (SuperPageLayout) LayoutInflater.from(getContext()).inflate(R.layout.page_mouse_free_mode_clean, null);
            centralXNumberSeekbar = hideButtonModeSettingPage.findViewById(R.id.page_mouse_free_mode_central_x);
            centralYNumberSeekbar = hideButtonModeSettingPage.findViewById(R.id.page_mouse_free_mode_central_y);
        }

        NumberSeekbar widthNumberSeekbar = hideButtonModeSettingPage.findViewById(R.id.page_mouse_free_mode_width);
        NumberSeekbar heightNumberSeekbar = hideButtonModeSettingPage.findViewById(R.id.page_mouse_free_mode_height);
        NumberSeekbar radiusNumberSeekbar = hideButtonModeSettingPage.findViewById(R.id.page_mouse_free_mode_radius);
        ElementEditText textElementEditText = hideButtonModeSettingPage.findViewById(R.id.page_mouse_free_mode_text);
        NumberSeekbar thickNumberSeekbar = hideButtonModeSettingPage.findViewById(R.id.page_mouse_free_mode_thick);
        NumberSeekbar layerNumberSeekbar = hideButtonModeSettingPage.findViewById(R.id.page_mouse_free_mode_layer);
        ElementEditText normalColorElementEditText = hideButtonModeSettingPage.findViewById(R.id.page_mouse_free_mode_normal_color);
        ElementEditText pressedColorElementEditText = hideButtonModeSettingPage.findViewById(R.id.page_mouse_free_mode_pressed_color);
        ElementEditText backgroundColorElementEditText = hideButtonModeSettingPage.findViewById(R.id.page_mouse_free_mode_background_color);
        NumberSeekbar textSizeNumberSeekbar = hideButtonModeSettingPage.findViewById(R.id.page_mouse_free_mode_text_size);
        ElementEditText normalTextColorElementEditText = hideButtonModeSettingPage.findViewById(R.id.page_mouse_free_mode_normal_text_color);
        ElementEditText pressedTextColorElementEditText = hideButtonModeSettingPage.findViewById(R.id.page_mouse_free_mode_pressed_text_color);
        Button copyButton = hideButtonModeSettingPage.findViewById(R.id.page_mouse_free_mode_copy);
        Button deleteButton = hideButtonModeSettingPage.findViewById(R.id.page_mouse_free_mode_delete);

        textElementEditText.setTextWithNoTextChangedCallBack(text);
        textElementEditText.setOnTextChangedListener(new ElementEditText.OnTextChangedListener() {
            @Override
            public void textChanged(String text) {
                setElementText(text);
                save();
            }
        });

        centralXNumberSeekbar.setProgressMin(centralXMin);
        centralXNumberSeekbar.setProgressMax(centralXMax);
        centralXNumberSeekbar.setValueWithNoCallBack(getElementCentralX());
        centralXNumberSeekbar.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                setElementCentralX(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                save();
            }
        });
        centralYNumberSeekbar.setProgressMin(centralYMin);
        centralYNumberSeekbar.setProgressMax(centralYMax);
        centralYNumberSeekbar.setValueWithNoCallBack(getElementCentralY());
        centralYNumberSeekbar.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                setElementCentralY(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                save();
            }
        });

        widthNumberSeekbar.setProgressMax(widthMax);
        widthNumberSeekbar.setProgressMin(widthMin);
        widthNumberSeekbar.setValueWithNoCallBack(getElementWidth());
        widthNumberSeekbar.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                setElementWidth(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                radiusNumberSeekbar.setProgressMax(Math.min(getElementWidth(), getElementHeight()) / 2);
                save();
            }
        });

        heightNumberSeekbar.setProgressMax(heightMax);
        heightNumberSeekbar.setProgressMin(heightMin);
        heightNumberSeekbar.setValueWithNoCallBack(getElementHeight());
        heightNumberSeekbar.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                setElementHeight(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                radiusNumberSeekbar.setProgressMax(Math.min(getElementWidth(), getElementHeight()) / 2);
                save();
            }
        });

        radiusNumberSeekbar.setProgressMax(Math.min(getElementWidth(), getElementHeight()) / 2);
        radiusNumberSeekbar.setValueWithNoCallBack(radius);
        radiusNumberSeekbar.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                setElementRadius(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                save();
            }
        });

        thickNumberSeekbar.setValueWithNoCallBack(thick);
        thickNumberSeekbar.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                setElementThick(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                save();
            }
        });

        layerNumberSeekbar.setValueWithNoCallBack(layer);
        layerNumberSeekbar.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                setElementLayer(seekBar.getProgress());
                save();
            }
        });

        textSizeNumberSeekbar.setProgressMin(10);
        textSizeNumberSeekbar.setProgressMax(150);
        textSizeNumberSeekbar.setValueWithNoCallBack(textSizePercent);
        textSizeNumberSeekbar.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                setElementTextSizePercent(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                save();
            }
        });

        // Color pickers
        setupColorPickerButton(normalColorElementEditText, () -> this.normalColor, this::setElementNormalColor);
        setupColorPickerButton(pressedColorElementEditText, () -> this.pressedColor, this::setElementPressedColor);
        setupColorPickerButton(backgroundColorElementEditText, () -> this.backgroundColor, this::setElementBackgroundColor);
        setupColorPickerButton(normalTextColorElementEditText, () -> this.normalTextColor, this::setElementNormalTextColor);
        setupColorPickerButton(pressedTextColorElementEditText, () -> this.pressedTextColor, this::setElementPressedTextColor);

        // Hide mode selection (mouse, trackpad, or multi-touch)
        RadioGroup hideModeRadioGroup = hideButtonModeSettingPage.findViewById(R.id.page_mouse_free_mode_hide_mode);
        if (hideModeRadioGroup != null) {
            if (hideMode == 2) {
                hideModeRadioGroup.check(R.id.page_mouse_free_mode_hide_mode_multitouch);
            } else if (hideMode == 1) {
                hideModeRadioGroup.check(R.id.page_mouse_free_mode_hide_mode_trackpad);
            } else {
                hideModeRadioGroup.check(R.id.page_mouse_free_mode_hide_mode_mouse);
            }
            hideModeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == R.id.page_mouse_free_mode_hide_mode_multitouch) {
                    hideMode = 2;
                } else if (checkedId == R.id.page_mouse_free_mode_hide_mode_trackpad) {
                    hideMode = 1;
                } else {
                    hideMode = 0;
                }
                save();
                elementController.setHideButtonModeHideMode(hideMode);
            });
        }

        // Select elements to hide button
        hideButtonModeSettingPage.findViewById(R.id.page_mouse_free_mode_select_hide_button).setOnClickListener(v -> {
            SuperPagesController superPagesController = elementController.getSuperPagesController();

            // Load saved hide element list
            hideElementList.clear();
            List<Long> savedIds = elementController.getHideButtonModeHideElementIds();
            for (Element element : elementController.getElements()) {
                if (savedIds.contains(element.elementId)) {
                    hideElementList.add(element);
                }
            }

            // Enter select mode
            elementController.changeMode(ElementController.Mode.Select);
            ElementSelectedCallBack elementSelectedCallBack = element -> {
                if (element == hideButtonMode) {
                    Toast.makeText(getContext(), getContext().getString(R.string.hide_button_mode_cannot_select_self), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (hideElementList.contains(element)) {
                    hideElementList.remove(element);
                    element.setEditColor(EDIT_COLOR_SELECT);
                } else {
                    hideElementList.add(element);
                    element.setEditColor(EDIT_COLOR_SELECTED);
                }
                element.invalidate();
            };
            // Mark already-selected elements
            for (Element element : hideElementList) {
                element.setEditColor(EDIT_COLOR_SELECTED);
            }
            for (Element element : elementController.getElements()) {
                element.setElementSelectedCallBack(elementSelectedCallBack);
            }

            SuperPageLayout pageNull = superPagesController.getPageNull();
            superPagesController.openNewPage(pageNull);
            pageNull.setPageReturnListener(() -> {
                SuperPageLayout lastPage = pageNull.getLastPage();
                elementController.open();
                superPagesController.openNewPage(lastPage);
                elementController.changeMode(ElementController.Mode.Edit);

                // Save selected element IDs
                saveHideElementIds();
            });
        });

        copyButton.setVisibility(View.GONE); // Only one MFM element should exist per config

        deleteButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                elementController.toggleInfoPage(hideButtonModeSettingPage);
                elementController.deleteElement(hideButtonMode);
            }
        });

        return hideButtonModeSettingPage;
    }

    /**
     * Save the list of element IDs to hide into the value field.
     */
    private void saveHideElementIds() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hideElementList.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(hideElementList.get(i).elementId);
        }

        if (sb.length() == 0) {
            value = "-1";
        } else {
            value = sb.toString();
        }

        // Update parsed IDs
        parseHideIds();

        // Save to DB
        save();

        // Update ElementController's hide list
        List<Long> ids = new ArrayList<>();
        for (Element element : hideElementList) {
            ids.add(element.elementId);
        }
        elementController.setHideButtonModeHideElements(ids);
    }

    protected void setElementText(String text) {
        this.text = text;
        invalidate();
    }

    protected void setElementRadius(int radius) {
        this.radius = radius;
        invalidate();
    }

    protected void setElementThick(int thick) {
        this.thick = thick;
        invalidate();
    }

    protected void setElementNormalColor(int normalColor) {
        this.normalColor = normalColor;
        invalidate();
    }

    protected void setElementPressedColor(int pressedColor) {
        this.pressedColor = pressedColor;
        invalidate();
    }

    protected void setElementBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
        invalidate();
    }

    protected void setElementNormalTextColor(int normalTextColor) {
        this.normalTextColor = normalTextColor;
        invalidate();
    }

    protected void setElementPressedTextColor(int pressedTextColor) {
        this.pressedTextColor = pressedTextColor;
        invalidate();
    }

    protected void setElementTextSizePercent(int textSizePercent) {
        this.textSizePercent = textSizePercent;
        invalidate();
    }

    public static ContentValues getInitialInfo() {
        ContentValues contentValues = new ContentValues();
        contentValues.put(COLUMN_INT_ELEMENT_TYPE, ELEMENT_TYPE_MOUSE_FREE_MODE);
        contentValues.put(COLUMN_STRING_ELEMENT_TEXT, "MFM");
        contentValues.put(COLUMN_STRING_ELEMENT_VALUE, "-1");
        contentValues.put(COLUMN_INT_ELEMENT_WIDTH, 100);
        contentValues.put(COLUMN_INT_ELEMENT_HEIGHT, 100);
        contentValues.put(COLUMN_INT_ELEMENT_LAYER, 50);
        contentValues.put(COLUMN_INT_ELEMENT_CENTRAL_X, 100);
        contentValues.put(COLUMN_INT_ELEMENT_CENTRAL_Y, 100);
        contentValues.put(COLUMN_INT_ELEMENT_RADIUS, 0);
        contentValues.put(COLUMN_INT_ELEMENT_THICK, 5);
        contentValues.put(COLUMN_INT_ELEMENT_NORMAL_COLOR, 0xF0888888);
        contentValues.put(COLUMN_INT_ELEMENT_PRESSED_COLOR, 0xF00000FF);
        contentValues.put(COLUMN_INT_ELEMENT_BACKGROUND_COLOR, 0x00FFFFFF);
        contentValues.put(COLUMN_INT_ELEMENT_NORMAL_TEXT_COLOR, 0xFFFFFFFF);
        contentValues.put(COLUMN_INT_ELEMENT_PRESSED_TEXT_COLOR, 0xFFCCCCCC);
        contentValues.put(COLUMN_INT_ELEMENT_TEXT_SIZE_PERCENT, 25);
        contentValues.put(COLUMN_INT_ELEMENT_MODE, 0); // 默认触控板模式
        return contentValues;
    }

    private interface IntSupplier {
        int get();
    }

    private interface IntConsumer {
        void accept(int value);
    }

    private void updateColorDisplay(ElementEditText colorDisplay, int color) {
        colorDisplay.setTextWithNoTextChangedCallBack(String.format("%08X", color));
        colorDisplay.setBackgroundColor(color);
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        colorDisplay.setTextColor(luminance > 0.5 ? Color.BLACK : Color.WHITE);
        colorDisplay.setGravity(Gravity.CENTER);
    }

    private void setupColorPickerButton(ElementEditText colorDisplay, IntSupplier initialColorFetcher, IntConsumer colorUpdater) {
        colorDisplay.setFocusable(false);
        colorDisplay.setCursorVisible(false);
        colorDisplay.setKeyListener(null);
        updateColorDisplay(colorDisplay, initialColorFetcher.get());
        colorDisplay.setOnClickListener(v -> {
            new ColorPickerDialog(
                    getContext(),
                    initialColorFetcher.get(),
                    true,
                    newColor -> {
                        colorUpdater.accept(newColor);
                        save();
                        updateColorDisplay(colorDisplay, newColor);
                    }
            ).show();
        });
    }
}

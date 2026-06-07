// 隐藏按键按钮
package com.limelight.binding.input.advance_setting.element;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.limelight.Game;
import com.limelight.R;
import com.limelight.binding.input.advance_setting.ControllerManager;
import com.limelight.binding.input.advance_setting.PageDeviceController;
import com.limelight.binding.input.advance_setting.config.PageConfigController;
import com.limelight.binding.input.advance_setting.superpage.ElementEditText;
import com.limelight.binding.input.advance_setting.superpage.NumberSeekbar;
import com.limelight.binding.input.advance_setting.superpage.SuperPageLayout;
import com.limelight.binding.input.advance_setting.superpage.SuperPagesController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 隐藏按键按钮：点击后隐藏/显示选中的子按键，同时切换触控模式。
 * 隐藏时切换到用户配置的触控模式（经典鼠标/触控板/多点触控），
 * 显示时恢复为触控板模式。
 */
public class HideKeysButton extends Element {

    private static final String COLUMN_INT_CHILD_VISIBILITY = COLUMN_INT_ELEMENT_SENSE;

    private static final int CHILD_VISIBLE = VISIBLE;
    private static final int CHILD_INVISIBLE = INVISIBLE;

    // 触控模式常量
    public static final String TOUCH_MODE_MOUSE = "MOUSE";
    public static final String TOUCH_MODE_TRACKPAD = "TRACKPAD";
    public static final String TOUCH_MODE_MULTI_TOUCH = "MULTI_TOUCH";

    public interface HideKeysButtonListener {
        void onClick();
        void onLongClick();
        void onRelease();
    }

    private PageDeviceController pageDeviceController;
    private HideKeysButton hideKeysButton;
    private List<Element> childElementList = new ArrayList<>();
    private ElementController elementController;
    private Context context;
    private SuperPagesController superPagesController;

    private HideKeysButtonListener listener;
    private String text;
    private String value;
    private int radius;
    private int thick;
    private int childVisibility;
    private int normalColor;
    private int pressedColor;
    private int backgroundColor;
    private int normalTextColor;
    private int pressedTextColor;
    private int textSizePercent;
    private String activeTouchMode; // 隐藏按键激活时的触控模式

    private SuperPageLayout hideKeysPage;
    private NumberSeekbar centralXNumberSeekbar;
    private NumberSeekbar centralYNumberSeekbar;
    private boolean selectMode = false;

    private long timerLongClickTimeout = 250;
    private final Runnable longClickRunnable = this::onLongClickCallback;
    private final Paint paintBorder = new Paint();
    private final Paint paintBackground = new Paint();
    private final Paint paintText = new Paint();
    private final Paint paintEdit = new Paint();
    private final RectF rect = new RectF();

    // 编辑模式长按拖动
    private static final long DRAG_EDIT_LONG_PRESS_TIMEOUT = 250;
    private boolean longPressDetected = false;
    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            longPressDetected = true;
            editColor = 0xff00f91a;
            invalidate();
        }
    };

    private float lastX;
    private float lastY;
    private boolean movable = false;
    private boolean layoutComplete = true;

    public HideKeysButton(Map<String, Object> attributesMap,
                          ElementController controller,
                          PageDeviceController pageDeviceController,
                          SuperPagesController superPagesController,
                          Context context) {
        super(attributesMap, controller, context);
        this.pageDeviceController = pageDeviceController;
        this.hideKeysButton = this;
        this.elementController = controller;
        this.context = context;
        this.superPagesController = superPagesController;

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
        childVisibility = ((Long) attributesMap.get(COLUMN_INT_CHILD_VISIBILITY)).intValue();
        normalColor = ((Long) attributesMap.get(COLUMN_INT_ELEMENT_NORMAL_COLOR)).intValue();
        pressedColor = ((Long) attributesMap.get(COLUMN_INT_ELEMENT_PRESSED_COLOR)).intValue();
        backgroundColor = ((Long) attributesMap.get(COLUMN_INT_ELEMENT_BACKGROUND_COLOR)).intValue();
        value = (String) attributesMap.get(COLUMN_STRING_ELEMENT_VALUE);

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

        // 从 extra_attributes 加载 activeTouchMode
        this.activeTouchMode = TOUCH_MODE_TRACKPAD; // 默认触控板
        if (attributesMap.containsKey(COLUMN_STRING_EXTRA_ATTRIBUTES)) {
            String extraAttrsJson = (String) attributesMap.get(COLUMN_STRING_EXTRA_ATTRIBUTES);
            if (extraAttrsJson != null && !extraAttrsJson.isEmpty()) {
                try {
                    JsonObject extraAttrs = JsonParser.parseString(extraAttrsJson).getAsJsonObject();
                    if (extraAttrs.has("activeTouchMode")) {
                        this.activeTouchMode = extraAttrs.get("activeTouchMode").getAsString();
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        listener = new HideKeysButtonListener() {
            @Override
            public void onClick() {
            }

            @Override
            public void onLongClick() {
            }

            @Override
            public void onRelease() {
                triggerAction();
            }
        };

        onModeChanged(controller.getMode());
    }

    public void linkChildElements(List<Element> allElements) {
        if (value == null || value.isEmpty()) {
            return;
        }

        java.util.Map<Long, Element> elementMap = new java.util.HashMap<>();
        for (Element el : allElements) {
            elementMap.put(el.elementId, el);
        }

        String[] childElementIds = value.split(",");
        childElementList.clear();

        for (String childElementIdString : childElementIds) {
            if (childElementIdString.equals("-1")) continue;
            try {
                Long childElementId = Long.parseLong(childElementIdString);
                Element child = elementMap.get(childElementId);
                if (child != null) {
                    childElementList.add(child);
                }
            } catch (NumberFormatException e) {
                // ignore
            }
        }

        setElementChildVisibility(childVisibility);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        layoutComplete = true;
        super.onLayout(changed, left, top, right, bottom);
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
        rect.right = getElementWidth() - rect.left;
        rect.bottom = getHeight() - rect.top;
        canvas.drawRoundRect(rect, radius, radius, paintBackground);
        canvas.drawRoundRect(rect, radius, radius, paintBorder);
        canvas.drawText(text, centerX, baselineY, paintText);

        ElementController.Mode currentMode = elementController.getMode();
        if (currentMode == ElementController.Mode.Edit || currentMode == ElementController.Mode.Select) {
            rect.left = rect.top = 2;
            rect.right = getWidth() - 2;
            rect.bottom = getHeight() - 2;

            if (currentMode == ElementController.Mode.Select && selectMode) {
                paintEdit.setColor(0xff00f91a);
            } else {
                paintEdit.setColor(editColor);
            }
            canvas.drawRect(rect, paintEdit);
        }
    }

    private void onClickCallback() {
        listener.onClick();
        elementController.getHandler().removeCallbacks(longClickRunnable);
        elementController.getHandler().postDelayed(longClickRunnable, timerLongClickTimeout);
    }

    private void onLongClickCallback() {
        listener.onLongClick();
    }

    private void onReleaseCallback() {
        listener.onRelease();
        elementController.getHandler().removeCallbacks(longClickRunnable);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionIndex() != 0) return true;

        switch (elementController.getMode()) {
            case Normal:
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        elementController.buttonVibrator();
                        lastX = event.getX();
                        lastY = event.getY();
                        setPressed(true);
                        onClickCallback();
                        invalidate();
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_UP:
                        setPressed(false);
                        onReleaseCallback();
                        invalidate();
                        return true;
                }
                break;

            case Edit:
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = event.getX();
                        lastY = event.getY();
                        movable = false;
                        longPressDetected = false;
                        editColor = 0xffdc143c;
                        invalidate();

                        elementController.getHandler().removeCallbacks(longPressRunnable);
                        elementController.getHandler().postDelayed(longPressRunnable, DRAG_EDIT_LONG_PRESS_TIMEOUT);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getX() - lastX;
                        float deltaY = event.getY() - lastY;

                        if (Math.abs(deltaX) + Math.abs(deltaY) < 0.2) {
                            return true;
                        }

                        if (!elementController.isDragEditEnabled() || longPressDetected) {
                            movable = true;
                            if (layoutComplete) {
                                layoutComplete = false;
                                ElementController.SnapResult snapResult = elementController.snapElementPosition(
                                        this,
                                        (int) getX() + getWidth() / 2 + (int) deltaX,
                                        (int) getY() + getHeight() / 2 + (int) deltaY
                                );
                                setElementCentralX(snapResult.centralX);
                                setElementCentralY(snapResult.centralY);
                            }
                            updatePage();
                        }
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_UP:
                        elementController.getHandler().removeCallbacks(longPressRunnable);
                        elementController.clearAlignmentGuides();

                        if (movable) {
                            save();
                            movable = false;
                        } else {
                            elementController.toggleInfoPage(getInfoPage());
                        }
                        editColor = 0xffdc143c;
                        invalidate();
                        return true;
                }
                break;
            default:
                return super.onTouchEvent(event);
        }
        return true;
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        return true;
    }

    @Override
    public void save() {
        ContentValues contentValues = new ContentValues();
        contentValues.put(COLUMN_STRING_ELEMENT_TEXT, text);
        contentValues.put(COLUMN_STRING_ELEMENT_VALUE, value);
        contentValues.put(COLUMN_INT_CHILD_VISIBILITY, childVisibility);
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
        contentValues.put(COLUMN_INT_ELEMENT_FLAG1, 0);
        contentValues.put(COLUMN_INT_ELEMENT_NORMAL_TEXT_COLOR, normalTextColor);
        contentValues.put(COLUMN_INT_ELEMENT_PRESSED_TEXT_COLOR, pressedTextColor);
        contentValues.put(COLUMN_INT_ELEMENT_TEXT_SIZE_PERCENT, textSizePercent);

        JsonObject extraAttrs = new JsonObject();
        extraAttrs.addProperty("activeTouchMode", this.activeTouchMode);
        contentValues.put(COLUMN_STRING_EXTRA_ATTRIBUTES, new Gson().toJson(extraAttrs));

        elementController.updateElement(elementId, contentValues);
    }

    @Override
    protected void updatePage() {
        if (hideKeysPage != null) {
            centralXNumberSeekbar.setValueWithNoCallBack(getElementCentralX());
            centralYNumberSeekbar.setValueWithNoCallBack(getElementCentralY());
        }
    }

    @Override
    protected SuperPageLayout getInfoPage() {
        if (hideKeysPage == null) {
            hideKeysPage = (SuperPageLayout) LayoutInflater.from(getContext()).inflate(R.layout.page_hide_keys_button, null);
            centralXNumberSeekbar = hideKeysPage.findViewById(R.id.page_hide_keys_central_x);
            centralYNumberSeekbar = hideKeysPage.findViewById(R.id.page_hide_keys_central_y);
        }

        setupInfoPage(hideKeysPage);
        return hideKeysPage;
    }

    private void setupInfoPage(SuperPageLayout page) {
        NumberSeekbar widthNumberSeekbar = page.findViewById(R.id.page_hide_keys_width);
        NumberSeekbar heightNumberSeekbar = page.findViewById(R.id.page_hide_keys_height);
        NumberSeekbar radiusNumberSeekbar = page.findViewById(R.id.page_hide_keys_radius);
        NumberSeekbar thickNumberSeekbar = page.findViewById(R.id.page_hide_keys_thick);
        NumberSeekbar layerNumberSeekbar = page.findViewById(R.id.page_hide_keys_layer);
        NumberSeekbar textSizeNumberSeekbar = page.findViewById(R.id.page_hide_keys_text_size);
        CheckBox childVisibleCheckBox = page.findViewById(R.id.page_hide_keys_child_visible);
        ElementEditText textElementEditText = page.findViewById(R.id.page_hide_keys_text);
        ElementEditText normalColorElementEditText = page.findViewById(R.id.page_hide_keys_normal_color);
        ElementEditText pressedColorElementEditText = page.findViewById(R.id.page_hide_keys_pressed_color);
        ElementEditText backgroundColorElementEditText = page.findViewById(R.id.page_hide_keys_background_color);
        ElementEditText normalTextColorElementEditText = page.findViewById(R.id.page_hide_keys_normal_text_color);
        ElementEditText pressedTextColorElementEditText = page.findViewById(R.id.page_hide_keys_pressed_text_color);
        RadioGroup touchModeGroup = page.findViewById(R.id.page_hide_keys_touch_mode_group);
        Button deleteButton = page.findViewById(R.id.page_hide_keys_delete);

        // 按键文本
        textElementEditText.setTextWithNoTextChangedCallBack(text);
        textElementEditText.setOnTextChangedListener(newText -> {
            text = newText;
            invalidate();
            save();
        });

        // 子按键可见性
        childVisibleCheckBox.setChecked(childVisibility == CHILD_VISIBLE);
        childVisibleCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setElementChildVisibility(isChecked ? CHILD_VISIBLE : CHILD_INVISIBLE);
            applyTouchModeForCurrentState();
            save();
        });

        // 触控模式选择
        switch (activeTouchMode) {
            case TOUCH_MODE_MOUSE:
                touchModeGroup.check(R.id.page_hide_keys_mode_mouse);
                break;
            case TOUCH_MODE_TRACKPAD:
                touchModeGroup.check(R.id.page_hide_keys_mode_trackpad);
                break;
            case TOUCH_MODE_MULTI_TOUCH:
                touchModeGroup.check(R.id.page_hide_keys_mode_multi_touch);
                break;
        }
        touchModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.page_hide_keys_mode_mouse) {
                activeTouchMode = TOUCH_MODE_MOUSE;
            } else if (checkedId == R.id.page_hide_keys_mode_trackpad) {
                activeTouchMode = TOUCH_MODE_TRACKPAD;
            } else if (checkedId == R.id.page_hide_keys_mode_multi_touch) {
                activeTouchMode = TOUCH_MODE_MULTI_TOUCH;
            }
            save();
        });

        // 位置
        centralXNumberSeekbar.setProgressMin(centralXMin);
        centralXNumberSeekbar.setProgressMax(centralXMax);
        centralXNumberSeekbar.setValueWithNoCallBack(getElementCentralX());
        centralXNumberSeekbar.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (layoutComplete) {
                    layoutComplete = false;
                    setElementCentralX(progress);
                }
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) { save(); }
        });

        centralYNumberSeekbar.setProgressMin(centralYMin);
        centralYNumberSeekbar.setProgressMax(centralYMax);
        centralYNumberSeekbar.setValueWithNoCallBack(getElementCentralY());
        centralYNumberSeekbar.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (layoutComplete) {
                    layoutComplete = false;
                    setElementCentralY(progress);
                }
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) { save(); }
        });

        // 尺寸
        widthNumberSeekbar.setProgressMax(widthMax);
        widthNumberSeekbar.setProgressMin(widthMin);
        widthNumberSeekbar.setValueWithNoCallBack(getElementWidth());
        widthNumberSeekbar.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (layoutComplete) {
                    layoutComplete = false;
                    setElementWidth(progress);
                }
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {
                radiusNumberSeekbar.setProgressMax(Math.min(getElementWidth(), getElementHeight()) / 2);
                save();
            }
        });

        heightNumberSeekbar.setProgressMax(heightMax);
        heightNumberSeekbar.setProgressMin(heightMin);
        heightNumberSeekbar.setValueWithNoCallBack(getElementHeight());
        heightNumberSeekbar.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (layoutComplete) {
                    layoutComplete = false;
                    setElementHeight(progress);
                }
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {
                radiusNumberSeekbar.setProgressMax(Math.min(getElementWidth(), getElementHeight()) / 2);
                save();
            }
        });

        // 圆角
        radiusNumberSeekbar.setProgressMax(Math.min(getElementWidth(), getElementHeight()) / 2);
        radiusNumberSeekbar.setValueWithNoCallBack(radius);
        radiusNumberSeekbar.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                radius = progress;
                invalidate();
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) { save(); }
        });

        // 边框粗细
        thickNumberSeekbar.setValueWithNoCallBack(thick);
        thickNumberSeekbar.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                thick = progress;
                invalidate();
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) { save(); }
        });

        // 图层
        layerNumberSeekbar.setValueWithNoCallBack(layer);
        layerNumberSeekbar.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {
                setElementLayer(seekBar.getProgress());
                save();
            }
        });

        // 文字大小
        textSizeNumberSeekbar.setProgressMin(10);
        textSizeNumberSeekbar.setProgressMax(150);
        textSizeNumberSeekbar.setValueWithNoCallBack(textSizePercent);
        textSizeNumberSeekbar.setOnNumberSeekbarChangeListener(new NumberSeekbar.OnNumberSeekbarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textSizePercent = progress;
                invalidate();
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) { save(); }
        });

        // 颜色
        CrownColorPickerBinder.bind(this, normalColorElementEditText, () -> this.normalColor, color -> { this.normalColor = color; invalidate(); save(); });
        CrownColorPickerBinder.bind(this, pressedColorElementEditText, () -> this.pressedColor, color -> { this.pressedColor = color; invalidate(); save(); });
        CrownColorPickerBinder.bind(this, backgroundColorElementEditText, () -> this.backgroundColor, color -> { this.backgroundColor = color; invalidate(); save(); });
        CrownColorPickerBinder.bind(this, normalTextColorElementEditText, () -> this.normalTextColor, color -> { this.normalTextColor = color; invalidate(); save(); });
        CrownColorPickerBinder.bind(this, pressedTextColorElementEditText, () -> this.pressedTextColor, color -> { this.pressedTextColor = color; invalidate(); save(); });

        // 选择被隐藏的按键
        page.findViewById(R.id.page_hide_keys_select_child_button).setOnClickListener(v -> {
            elementController.changeMode(ElementController.Mode.Select);
            selectMode = true;
            ElementSelectedCallBack elementSelectedCallBack = element -> {
                if (childElementList.contains(element)) {
                    childElementList.remove(element);
                    updateValueString();
                    element.setEditColor(EDIT_COLOR_SELECT);
                } else {
                    if (element == hideKeysButton) {
                        Toast.makeText(context, "不能将自身添加为被隐藏按键", Toast.LENGTH_SHORT).show();
                    } else {
                        childElementList.add(element);
                        updateValueString();
                        element.setEditColor(EDIT_COLOR_SELECTED);
                    }
                }
                element.invalidate();
            };
            for (Element element : childElementList) element.setEditColor(EDIT_COLOR_SELECTED);
            for (Element element : elementController.getElements())
                element.setElementSelectedCallBack(elementSelectedCallBack);

            SuperPageLayout pageNull = superPagesController.getPageNull();
            superPagesController.openNewPage(pageNull);
            pageNull.setPageReturnListener(() -> {
                SuperPageLayout lastPage = pageNull.getLastPage();
                elementController.open();
                superPagesController.openNewPage(lastPage);
                elementController.changeMode(ElementController.Mode.Edit);
                selectMode = false;
                save();
            });
        });

        // 删除
        deleteButton.setOnClickListener(v -> {
            elementController.toggleInfoPage(hideKeysPage);
            elementController.deleteElement(hideKeysButton);
            Toast.makeText(context, "删除成功", Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * 点击按钮时的触发动作：切换子元素可见性并切换触控模式
     */
    public void triggerAction() {
        int targetVisibility = (childVisibility == CHILD_VISIBLE) ? CHILD_INVISIBLE : CHILD_VISIBLE;
        setElementChildVisibility(targetVisibility);
        applyTouchModeForCurrentState();
        save();
    }

    /**
     * 根据当前子按键可见性状态，应用对应的触控模式
     */
    private void applyTouchModeForCurrentState() {
        ControllerManager controllerManager = elementController.getControllerManager();
        if (controllerManager == null || controllerManager.getTouchController() == null) return;

        ContentValues contentValues = new ContentValues();

        if (childVisibility == CHILD_INVISIBLE) {
            // 按键被隐藏时，切换到用户配置的触控模式
            switch (activeTouchMode) {
                case TOUCH_MODE_MOUSE:
                    controllerManager.getTouchController().setTouchMode(false);
                    controllerManager.getTouchController().setEnhancedTouch(false);
                    contentValues.put(PageConfigController.COLUMN_BOOLEAN_TOUCH_MODE, String.valueOf(false));
                    contentValues.put(PageConfigController.COLUMN_BOOLEAN_ENHANCED_TOUCH, String.valueOf(false));
                    elementController.showToast("经典鼠标模式");
                    break;
                case TOUCH_MODE_TRACKPAD:
                    controllerManager.getTouchController().setTouchMode(true);
                    controllerManager.getTouchController().setEnhancedTouch(false);
                    contentValues.put(PageConfigController.COLUMN_BOOLEAN_TOUCH_MODE, String.valueOf(true));
                    contentValues.put(PageConfigController.COLUMN_BOOLEAN_ENHANCED_TOUCH, String.valueOf(false));
                    elementController.showToast("触控板模式");
                    break;
                case TOUCH_MODE_MULTI_TOUCH:
                    controllerManager.getTouchController().setTouchMode(false);
                    controllerManager.getTouchController().setEnhancedTouch(true);
                    contentValues.put(PageConfigController.COLUMN_BOOLEAN_TOUCH_MODE, String.valueOf(false));
                    contentValues.put(PageConfigController.COLUMN_BOOLEAN_ENHANCED_TOUCH, String.valueOf(true));
                    elementController.showToast("多点触控模式");
                    break;
            }
        } else {
            // 按键显示时，恢复为触控板模式
            controllerManager.getTouchController().setTouchMode(true);
            controllerManager.getTouchController().setEnhancedTouch(false);
            contentValues.put(PageConfigController.COLUMN_BOOLEAN_TOUCH_MODE, String.valueOf(true));
            contentValues.put(PageConfigController.COLUMN_BOOLEAN_ENHANCED_TOUCH, String.valueOf(false));
            elementController.showToast("触控板模式");
        }

        controllerManager.getSuperConfigDatabaseHelper().updateConfig(
                controllerManager.getPageConfigController().getCurrentConfigId(), contentValues);
    }

    private void setElementChildVisibility(int visibility) {
        this.childVisibility = visibility;
        for (Element child : childElementList) {
            child.setVisibility(visibility);
        }
    }

    private void updateValueString() {
        StringBuilder newValue = new StringBuilder("-1");
        for (Element element : childElementList) {
            newValue.append(",").append(element.elementId);
        }
        value = newValue.toString();
    }

    @Override
    public void onModeChanged(ElementController.Mode newMode) {
        super.onModeChanged(newMode);

        switch (newMode) {
            case Select:
                setEditColor(EDIT_COLOR_SELECT);
                break;
            case Edit:
                setEditColor(EDIT_COLOR_EDIT);
                // 进入编辑模式时临时显示所有子按键，方便用户查看已选中的按键
                for (Element child : childElementList) {
                    child.setVisibility(VISIBLE);
                }
                break;
            case Normal:
                setEditColor(EDIT_COLOR_EDIT);
                // 退出编辑模式时恢复子按键的实际存储状态
                for (Element child : childElementList) {
                    child.setVisibility(childVisibility);
                }
                break;
        }
    }

    public static ContentValues getInitialInfo() {
        ContentValues contentValues = new ContentValues();
        contentValues.put(COLUMN_INT_ELEMENT_TYPE, ELEMENT_TYPE_HIDE_KEYS_BUTTON);
        contentValues.put(COLUMN_STRING_ELEMENT_TEXT, "HIDE");
        contentValues.put(COLUMN_STRING_ELEMENT_VALUE, "-1");
        contentValues.put(COLUMN_INT_CHILD_VISIBILITY, VISIBLE);
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
        contentValues.put(COLUMN_INT_ELEMENT_FLAG1, 0);

        JsonObject extraAttrs = new JsonObject();
        extraAttrs.addProperty("activeTouchMode", TOUCH_MODE_TRACKPAD);
        contentValues.put(COLUMN_STRING_EXTRA_ATTRIBUTES, new Gson().toJson(extraAttrs));

        return contentValues;
    }
}

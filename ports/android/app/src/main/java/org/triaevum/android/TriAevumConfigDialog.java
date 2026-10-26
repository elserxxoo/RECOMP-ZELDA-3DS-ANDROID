package org.triaevum.android;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import org.triaevum.android.controls.WindroidVirtualControllerView;

/**
 * Full-screen settings dialog styled after WINDROID-EMU/Zenda.
 *
 * Opens as an in-process Dialog (not a separate Activity) so the Vulkan
 * swapchain surface is never destroyed during configuration.
 *
 * NOTE: Does NOT touch EmulationMenuSettings (Kotlin object) at construction
 * time — that class uses OverlayHost.appContext in its static initialiser and
 * will throw if the overlay host is not yet bound.  All overlay preferences
 * are accessed directly via SharedPreferences with the same key names.
 */
public final class TriAevumConfigDialog extends Dialog {

    private static final String TAG = "TriAevumCfg";
    // PreferenceManager.getDefaultSharedPreferences() uses "<packageName>_preferences"
    private static final String PREFS_NAME = "org.triaevum.android_preferences";
    private final TriAevumConfigManager mConfig;
    private final WindroidVirtualControllerView mOverlay;
    private SharedPreferences mPrefs;

    // Tabs
    private Button mTabGeral, mTabGraficos, mTabCamera, mTabControles;
    private Button mCurrentTab;

    // Panels
    private View mPanelGeral, mPanelGraficos, mPanelCamera, mPanelControles;

    // Geral
    private Spinner mSpLanguage;
    private Spinner mSpSurfaceRes;
    private TextView mTvSaveStatus;
    private Button mBtnExportSave;
    private Button mBtnImportSave;

    // Gráficos
    private Spinner mSpRenderScale;
    private Spinner mSpAAMode;
    private Spinner mSpFramerate;
    private Spinner mSpInterp;
    private CheckBox mCbVSync;
    private CheckBox mCbShowFps;
    private CheckBox mCbCustomTextures;
    private CheckBox mCbPreloadTextures;
    private TextView mTvCustomTexturesPath;
    private TextView mTvCustomTexturesCount;
    private Button   mBtnSelectTexturesDir;
    private Button   mBtnResetTexturesDir;
    private Button   mBtnPreloadNow;

    // Driver Vulkan / Turnip
    private Spinner  mSpGpuDriver;
    private Button   mBtnInstallDriver;
    private Button   mBtnDeleteDriver;
    private TextView mTvDriverInfo;
    private TriAevumCustomDriverManager mCustomDriverManager;
    private java.util.List<TriAevumCustomDriverManager.CustomDriverInfo> mInstalledDrivers = new java.util.ArrayList<>();

    // Mods Visuais
    private CheckBox mCbToonShading;
    private CheckBox mCbToonOutline;
    private View     mLayoutToonOutline;
    private Spinner  mSpGrassQuality;
    private SeekBar  mSbFov;
    private TextView mTvFovLabel;

    // Câmera / HUD
    private CheckBox mCbFreeCamera;
    private SeekBar  mSbCamSpeed;
    private TextView mTvCamSpeedLabel;
    private CheckBox mCbCamInvertX;
    private CheckBox mCbCamInvertY;
    private Spinner  mSpHudLayout;
    private SeekBar  mSbHudScale;
    private TextView mTvHudScaleLabel;
    private SeekBar  mSbHudMarginX;
    private TextView mTvHudMarginXLabel;
    private SeekBar  mSbHudMarginY;
    private TextView mTvHudMarginYLabel;
    private CheckBox mCbMinimap;
    private CheckBox mCbDpadIcons;
    private CheckBox mCbItemsHint;

    // Controles
    private CheckBox mCbShowOverlay;
    private SeekBar  mSbOpacity;
    private TextView mTvOpacityLabel;
    private CheckBox mCbHaptic;
    private CheckBox mCbSwapScreens;
    private CheckBox mCbGyro;
    private CheckBox mCbTouchScreen;
    private CheckBox mCbJoystickRelCenter;
    private CheckBox mCbDpadSlide;
    private final Activity mActivity;
    private boolean mSaving = false;

    public TriAevumConfigDialog(Activity owner, WindroidVirtualControllerView overlay) {
        super(owner, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        mActivity = owner;
        mConfig  = new TriAevumConfigManager(owner);
        mOverlay = overlay;
        // PreferenceManager.getDefaultSharedPreferences() → "<pkg>_preferences"
        mPrefs = owner.getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Log.d(TAG, "Dialog created. Prefs file: " + PREFS_NAME);
    }

    @Override
    public void dismiss() {
        saveToConfig();
        mConfig.applyLiveSettings();
        if (mActivity instanceof TriAevumActivity) {
            ((TriAevumActivity) mActivity).updateFpsCounterVisibility();
        }
        super.dismiss();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Stay fullscreen / immersive inside the dialog
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);

        setContentView(R.layout.dialog_config);

        // Ensure dialog fills the entire screen
        Window w = getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        bindViews();
        loadFromConfig();
        setupTabs();
        setupButtons();
    }

    // -------------------------------------------------------------------------
    // View binding
    // -------------------------------------------------------------------------

    private void bindViews() {
        mTabGeral    = findViewById(R.id.tab_geral);
        mTabGraficos = findViewById(R.id.tab_graficos);
        mTabCamera   = findViewById(R.id.tab_camera);
        mTabControles= findViewById(R.id.tab_controles);

        mPanelGeral    = findViewById(R.id.panel_geral);
        mPanelGraficos = findViewById(R.id.panel_graficos);
        mPanelCamera   = findViewById(R.id.panel_camera);
        mPanelControles= findViewById(R.id.panel_controles);

        // Geral
        mSpLanguage   = findViewById(R.id.sp_language);
        mSpSurfaceRes = findViewById(R.id.sp_surface_resolution);
        mTvSaveStatus = findViewById(R.id.tv_save_status);
        mBtnExportSave = findViewById(R.id.btn_export_save);
        mBtnImportSave = findViewById(R.id.btn_import_save);

        // Gráficos
        mSpRenderScale    = findViewById(R.id.sp_render_scale);
        mSpAAMode         = findViewById(R.id.sp_aa_mode);
        mSpFramerate      = findViewById(R.id.sp_framerate);
        mSpInterp         = findViewById(R.id.sp_interp_strictness);
        mCbVSync          = findViewById(R.id.cb_vsync);
        mCbShowFps        = findViewById(R.id.cb_show_fps);
        mCbCustomTextures = findViewById(R.id.cb_custom_textures);
        mCbPreloadTextures = findViewById(R.id.cb_preload_textures);
        mTvCustomTexturesPath = findViewById(R.id.tv_custom_textures_path);
        mTvCustomTexturesCount = findViewById(R.id.tv_custom_textures_count);
        mBtnSelectTexturesDir = findViewById(R.id.btn_select_textures_dir);
        mBtnResetTexturesDir  = findViewById(R.id.btn_reset_textures_dir);
        mBtnPreloadNow = findViewById(R.id.btn_preload_now);

        // Driver Vulkan / Turnip
        mSpGpuDriver       = findViewById(R.id.sp_gpu_driver);
        mBtnInstallDriver  = findViewById(R.id.btn_install_driver);
        mBtnDeleteDriver   = findViewById(R.id.btn_delete_driver);
        mTvDriverInfo      = findViewById(R.id.tv_driver_info);
        mCustomDriverManager = new TriAevumCustomDriverManager(mActivity);

        // Mods Visuais
        mCbToonShading     = findViewById(R.id.cb_toon_shading);
        mCbToonOutline     = findViewById(R.id.cb_toon_outline);
        mLayoutToonOutline = findViewById(R.id.layout_toon_outline);
        mSpGrassQuality    = findViewById(R.id.sp_grass_quality);
        mSbFov             = findViewById(R.id.sb_fov);
        mTvFovLabel        = findViewById(R.id.tv_fov_label);

        // Câmera / HUD
        mCbFreeCamera      = findViewById(R.id.cb_free_camera);
        mSbCamSpeed        = findViewById(R.id.sb_cam_speed);
        mTvCamSpeedLabel   = findViewById(R.id.tv_cam_speed_label);
        mCbCamInvertX      = findViewById(R.id.cb_cam_invert_x);
        mCbCamInvertY      = findViewById(R.id.cb_cam_invert_y);
        mSpHudLayout       = findViewById(R.id.sp_hud_layout);
        mSbHudScale        = findViewById(R.id.sb_hud_scale);
        mTvHudScaleLabel   = findViewById(R.id.tv_hud_scale_label);
        mSbHudMarginX      = findViewById(R.id.sb_hud_margin_x);
        mTvHudMarginXLabel = findViewById(R.id.tv_hud_margin_x_label);
        mSbHudMarginY      = findViewById(R.id.sb_hud_margin_y);
        mTvHudMarginYLabel = findViewById(R.id.tv_hud_margin_y_label);
        mCbMinimap         = findViewById(R.id.cb_minimap);
        mCbDpadIcons       = findViewById(R.id.cb_dpad_icons);
        mCbItemsHint       = findViewById(R.id.cb_items_hint);

        // Controles
        mCbShowOverlay       = findViewById(R.id.cb_show_overlay);
        mSbOpacity           = findViewById(R.id.sb_overlay_opacity);
        mTvOpacityLabel      = findViewById(R.id.tv_opacity_label);
        mCbHaptic            = findViewById(R.id.cb_haptic);
        mCbSwapScreens       = findViewById(R.id.cb_swap_screens);
        mCbGyro              = findViewById(R.id.cb_gyro);
        mCbTouchScreen       = findViewById(R.id.cb_touch_screen);
        mCbJoystickRelCenter = findViewById(R.id.cb_joystick_rel_center);
        mCbDpadSlide         = findViewById(R.id.cb_dpad_slide);
    }

    // -------------------------------------------------------------------------
    // Load current config into UI
    // -------------------------------------------------------------------------

    private void loadFromConfig() {
        Log.d(TAG, "--- loadFromConfig START ---");
        // Language
        setupSpinner(mSpLanguage, TriAevumConfigManager.LANGUAGE_LABELS, null);
        String langCode = mConfig.getLanguageCode();
        Log.d(TAG, "  language: " + langCode);
        for (int i = 0; i < TriAevumConfigManager.LANGUAGE_CODES.length; i++) {
            if (TriAevumConfigManager.LANGUAGE_CODES[i].equals(langCode)) {
                mSpLanguage.setSelection(i);
                break;
            }
        }

        // Surface resolution
        setupSpinner(mSpSurfaceRes, TriAevumConfigManager.SURFACE_RES_LABELS, null);
        int surfEdge = mConfig.getSurfaceMaxShortEdge();
        Log.d(TAG, "  surface_max_short_edge: " + surfEdge);
        int matchedResIdx = 2; // Default 720p
        for (int i = 0; i < TriAevumConfigManager.SURFACE_RES_VALUES.length; i++) {
            if (TriAevumConfigManager.SURFACE_RES_VALUES[i] == surfEdge) {
                matchedResIdx = i;
                break;
            }
        }
        mSpSurfaceRes.setSelection(matchedResIdx);

        // Render scale
        setupSpinner(mSpRenderScale, TriAevumConfigManager.RENDER_SCALE_LABELS, null);
        float rs = mConfig.getRenderScale();
        int matchedRsIdx = 4; // Default 1.0x
        float minRsDiff = Float.MAX_VALUE;
        for (int i = 0; i < TriAevumConfigManager.RENDER_SCALE_VALUES.length; i++) {
            float diff = Math.abs(TriAevumConfigManager.RENDER_SCALE_VALUES[i] - rs);
            if (diff < minRsDiff) {
                minRsDiff = diff;
                matchedRsIdx = i;
            }
        }
        mSpRenderScale.setSelection(matchedRsIdx);

        // AA
        setupSpinner(mSpAAMode, TriAevumConfigManager.AA_MODE_LABELS, null);
        String aaMode = mConfig.getAAMode();
        for (int i = 0; i < TriAevumConfigManager.AA_MODE_VALUES.length; i++) {
            if (TriAevumConfigManager.AA_MODE_VALUES[i].equals(aaMode)) {
                mSpAAMode.setSelection(i);
                break;
            }
        }

        // Framerate
        setupSpinner(mSpFramerate, TriAevumConfigManager.FRAMERATE_LABELS, null);
        setupSpinner(mSpInterp, RenderTuning.LABELS, RenderTuning.readLevel(getContext()));
        String frMode = mConfig.getFrameRateMode();
        for (int i = 0; i < TriAevumConfigManager.FRAMERATE_VALUES.length; i++) {
            if (TriAevumConfigManager.FRAMERATE_VALUES[i].equals(frMode)) {
                mSpFramerate.setSelection(i);
                break;
            }
        }

        mCbVSync.setChecked(mConfig.isVSync());
        if (mCbShowFps != null) {
            android.content.SharedPreferences fpsPrefs = mActivity.getApplicationContext().getSharedPreferences(
                "org.triaevum.android_preferences", Context.MODE_PRIVATE);
            mCbShowFps.setOnCheckedChangeListener(null);
            mCbShowFps.setChecked(fpsPrefs.getBoolean("show_game_fps_counter", false));
            mCbShowFps.setOnCheckedChangeListener((buttonView, isChecked) -> {
                fpsPrefs.edit().putBoolean("show_game_fps_counter", isChecked).apply();
                if (mActivity instanceof TriAevumActivity) {
                    ((TriAevumActivity) mActivity).updateFpsCounterVisibility();
                }
            });
        }
        mCbCustomTextures.setChecked(mConfig.isCustomTexturesEnabled());
        if (mCbPreloadTextures != null) {
            mCbPreloadTextures.setChecked(mConfig.isCustomTexturesPreloadEnabled());
        }
        updateTextureDirectoryDisplay();
        Log.d(TAG, "  renderScale=" + mConfig.getRenderScale()
            + " AA=" + mConfig.getAAMode()
            + " FR=" + mConfig.getFrameRateMode()
            + " vsync=" + mConfig.isVSync()
            + " customTex=" + mConfig.isCustomTexturesEnabled());

        // Visual Mods (Toon, Grass, FOV)
        boolean toon = mConfig.isToonEnabled();
        boolean outline = mConfig.isToonOutlineEnabled();
        mCbToonShading.setChecked(toon);
        mCbToonOutline.setChecked(outline);
        mCbToonOutline.setEnabled(toon);
        if (mLayoutToonOutline != null) {
            mLayoutToonOutline.setAlpha(toon ? 1.0f : 0.45f);
        }
        mCbToonShading.setOnCheckedChangeListener((btn, isChecked) -> {
            mCbToonOutline.setEnabled(isChecked);
            if (mLayoutToonOutline != null) {
                mLayoutToonOutline.setAlpha(isChecked ? 1.0f : 0.45f);
            }
        });

        setupSpinner(mSpGrassQuality, TriAevumConfigManager.GRASS_QUALITY_LABELS, null);
        String grass = mConfig.getGrassQuality();
        for (int i = 0; i < TriAevumConfigManager.GRASS_QUALITY_VALUES.length; i++) {
            if (TriAevumConfigManager.GRASS_QUALITY_VALUES[i].equalsIgnoreCase(grass)) {
                mSpGrassQuality.setSelection(i);
                break;
            }
        }

        float fov = mConfig.getFovMultiplier();
        int fovProgress = Math.max(0, Math.min(5, Math.round((fov - 1.0f) / 0.05f)));
        mSbFov.setProgress(fovProgress);
        int fovPct = 100 + (fovProgress * 5);
        mTvFovLabel.setText("Field of View (FOV): " + fovPct + "%" + (fovProgress == 0 ? " (Default)" : ""));

        mSbFov.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int p = 100 + (progress * 5);
                mTvFovLabel.setText("Field of View (FOV): " + p + "%" + (progress == 0 ? " (Default)" : ""));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        // Camera / HUD
        mCbFreeCamera.setChecked(mConfig.isFreeCameraEnabled());
        int speedLevel = mConfig.getFreeCameraSpeedLevel();
        mSbCamSpeed.setProgress(speedLevel - 1); // 0-4 → levels 1-5
        mTvCamSpeedLabel.setText("Speed: " + speedLevel);
        mCbCamInvertX.setChecked(mConfig.isFreeCameraInvertX());
        mCbCamInvertY.setChecked(mConfig.isFreeCameraInvertY());

        // HUD Layout
        setupSpinner(mSpHudLayout, TriAevumConfigManager.HUD_LAYOUT_LABELS, null);
        String hudLayout = mConfig.getHudLayout();
        for (int i = 0; i < TriAevumConfigManager.HUD_LAYOUT_VALUES.length; i++) {
            if (TriAevumConfigManager.HUD_LAYOUT_VALUES[i].equalsIgnoreCase(hudLayout)) {
                mSpHudLayout.setSelection(i);
                break;
            }
        }

        float hudScale = mConfig.getHudScale();
        int hudPct = Math.round(hudScale * 100);
        // map 0.5-1.5 → seekbar 0-10
        int hudProg = Math.round((hudScale - 0.5f) / 0.1f);
        mSbHudScale.setProgress(Math.max(0, Math.min(10, hudProg)));
        mTvHudScaleLabel.setText("HUD Scale: " + hudPct + "%");

        // Margins (-3..16 -> seekbar 0..19)
        int marginX = mConfig.getHudMarginX();
        int marginY = mConfig.getHudMarginY();
        mSbHudMarginX.setProgress(Math.max(0, Math.min(19, marginX + 3)));
        mTvHudMarginXLabel.setText("Margin X (Horizontal): " + marginX);
        mSbHudMarginY.setProgress(Math.max(0, Math.min(19, marginY + 3)));
        mTvHudMarginYLabel.setText("Margin Y (Vertical): " + marginY);

        mCbMinimap.setChecked(mConfig.isMinimapVisible());
        mCbDpadIcons.setChecked(mConfig.isRenderDpadIcons());
        mCbItemsHint.setChecked(mConfig.isRenderItemsHint());
        Log.d(TAG, "  freeCamera=" + mConfig.isFreeCameraEnabled()
            + " speed=" + mConfig.getFreeCameraSpeedLevel()
            + " invertX=" + mConfig.isFreeCameraInvertX()
            + " invertY=" + mConfig.isFreeCameraInvertY()
            + " hudLayout=" + mConfig.getHudLayout()
            + " hudMarginX=" + marginX
            + " hudMarginY=" + marginY
            + " hudScale=" + mConfig.getHudScale()
            + " minimap=" + mConfig.isMinimapVisible()
            + " dpadIcons=" + mConfig.isRenderDpadIcons()
            + " itemsHint=" + mConfig.isRenderItemsHint());

        // Controls — read directly from SharedPreferences (same keys as EmulationMenuSettings)
        boolean showOverlay = (mOverlay != null) ? mOverlay.isShowControls() : mPrefs.getBoolean("EmulationMenuSettings_ShowOverlay", true);
        boolean haptic      = (mOverlay != null) ? mOverlay.isHapticFeedbackEnabled() : mPrefs.getBoolean("EmulationMenuSettings_HapticFeedback", true);
        boolean swapScr     = mPrefs.getBoolean("EmulationMenuSettings_SwapScreens", false);
        boolean touchScreen = (mOverlay != null) ? mOverlay.isTouchEnabled() : mPrefs.getBoolean("EmulationMenuSettings_TouchEnabled", false);
        boolean joyRel      = mPrefs.getBoolean("EmulationMenuSettings_JoystickRelCenter", true);
        boolean dpadSlide   = mPrefs.getBoolean("EmulationMenuSettings_DpadSlideEnable", true);
        Log.d(TAG, "  [prefs=" + PREFS_NAME + "]"
            + " showOverlay=" + showOverlay
            + " haptic=" + haptic
            + " swapScreens=" + swapScr
            + " touchScreen=" + touchScreen
            + " joystickRelCenter=" + joyRel
            + " dpadSlide=" + dpadSlide);
        mCbShowOverlay.setChecked(showOverlay);
        mCbHaptic.setChecked(haptic);
        mCbSwapScreens.setChecked(swapScr);
        boolean gyroAvailable = MotionSensorBridge.hasGyroscope(getContext());
        mCbGyro.setEnabled(gyroAvailable);
        mCbGyro.setChecked(gyroAvailable && mPrefs.getBoolean(MotionSensorBridge.PREF_KEY, false));
        if (!gyroAvailable) {
            ((android.widget.TextView) findViewById(R.id.tv_gyro_label))
                .setText("Gyroscope (not available on this device)");
        }
        mCbTouchScreen.setChecked(touchScreen);
        mCbJoystickRelCenter.setChecked(joyRel);
        mCbDpadSlide.setChecked(dpadSlide);

        // Opacity from overlay
        int overlayOpacity = 100;
        if (mOverlay != null) overlayOpacity = mOverlay.getOverlayOpacityPercent();
        mSbOpacity.setProgress(overlayOpacity);
        mTvOpacityLabel.setText("Controls Opacity: " + overlayOpacity + "%");

        // SeekBar live listeners
        mSbCamSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                mTvCamSpeedLabel.setText("Speed: " + (progress + 1));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        mSbHudScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                float scale = 0.5f + progress * 0.1f;
                mTvHudScaleLabel.setText("HUD Scale: " + Math.round(scale * 100) + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        mSbHudMarginX.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int val = progress - 3;
                mTvHudMarginXLabel.setText("Margin X (Horizontal): " + val);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        mSbHudMarginY.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int val = progress - 3;
                mTvHudMarginYLabel.setText("Margin Y (Vertical): " + val);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        mSbOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int pct = Math.max(20, progress); // minimum 20%
                mTvOpacityLabel.setText("Controls Opacity: " + pct + "%");
                if (fromUser && mOverlay != null) {
                    mOverlay.setOverlayOpacityPercent(pct);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        refreshSaveStatus();
        loadGpuDriversList();
    }

    public void refreshSaveStatus() {
        if (mTvSaveStatus != null) {
            String summary = TriAevumSaveManager.getSaveSummary(getContext());
            mTvSaveStatus.setText(summary);
        }
    }

    // -------------------------------------------------------------------------
    // Tab navigation
    // -------------------------------------------------------------------------

    private void setupTabs() {
        mCurrentTab = mTabGeral;

        mTabGeral.setOnClickListener(v -> selectTab(mTabGeral, mPanelGeral));
        mTabGraficos.setOnClickListener(v -> selectTab(mTabGraficos, mPanelGraficos));
        mTabCamera.setOnClickListener(v -> selectTab(mTabCamera, mPanelCamera));
        mTabControles.setOnClickListener(v -> selectTab(mTabControles, mPanelControles));
    }

    private void selectTab(Button tab, View panel) {
        // Deselect previous
        if (mCurrentTab != null) {
            mCurrentTab.setTextColor(Color.parseColor("#8B9BB4"));
            mCurrentTab.setBackgroundResource(R.drawable.btn_dark);
        }
        // Select new
        tab.setTextColor(Color.parseColor("#FFD700"));
        tab.setBackgroundResource(R.drawable.tab_selected_bg);
        mCurrentTab = tab;

        // Hide all panels then show selected
        mPanelGeral.setVisibility(View.GONE);
        mPanelGraficos.setVisibility(View.GONE);
        mPanelCamera.setVisibility(View.GONE);
        mPanelControles.setVisibility(View.GONE);
        panel.setVisibility(View.VISIBLE);
    }

    public void scrollToGpuDriver() {
        if (mTabGraficos != null && mPanelGraficos != null) {
            selectTab(mTabGraficos, mPanelGraficos);
            mPanelGraficos.post(() -> {
                if (mPanelGraficos instanceof android.widget.ScrollView) {
                    ((android.widget.ScrollView) mPanelGraficos).fullScroll(View.FOCUS_DOWN);
                }
            });
        }
    }

    // -------------------------------------------------------------------------
    // Header buttons
    // -------------------------------------------------------------------------

    private void setupButtons() {
        Button btnSave    = findViewById(R.id.btn_config_save);
        Button btnClose   = findViewById(R.id.btn_config_close);
        Button btnRestore = findViewById(R.id.btn_config_restore);

        btnSave.setOnClickListener(v -> {
            saveToConfig();
            mConfig.applyLiveSettings();
            Toast.makeText(getContext(), "Settings saved and applied!", Toast.LENGTH_SHORT).show();
            dismiss();
        });

        btnClose.setOnClickListener(v -> {
            dismiss();
        });

        btnRestore.setOnClickListener(v -> {
            mConfig.restoreDefaults();
            mConfig.applyLiveSettings();
            mPrefs.edit()
                .putBoolean("EmulationMenuSettings_ShowOverlay", true)
                .putBoolean("EmulationMenuSettings_HapticFeedback", true)
                .putBoolean("EmulationMenuSettings_SwapScreens", false)
                .putBoolean("EmulationMenuSettings_TouchEnabled", false)
                .putBoolean("EmulationMenuSettings_JoystickRelCenter", true)
                .putBoolean("EmulationMenuSettings_DpadSlideEnable", true)
                .apply();
            if (mOverlay != null) {
                mOverlay.setShowControls(true);
                mOverlay.setHapticFeedbackEnabled(true);
                mOverlay.setTouchEnabled(false);
                mOverlay.setOverlayOpacityPercent(100);
            }
            try {
                AndroidNativeInputTarget.nativeSwapScreens(false);
                AndroidNativeInputTarget.nativeSetTouchEnabled(false);
            } catch (Throwable t) {
                Log.w(TAG, "Failed to reset native control settings", t);
            }
            loadFromConfig();
            Toast.makeText(getContext(), "Defaults restored and applied.", Toast.LENGTH_SHORT).show();
        });

        Button btnEditHud = findViewById(R.id.btn_edit_hud_layout);
        if (btnEditHud != null) {
            btnEditHud.setOnClickListener(v -> {
                dismiss();
                if (mActivity instanceof TriAevumActivity) {
                    ((TriAevumActivity) mActivity).openHudLayoutEditor();
                }
            });
        }

        Button btnResetHud = findViewById(R.id.btn_reset_hud_layout);
        if (btnResetHud != null) {
            btnResetHud.setOnClickListener(v -> {
                if (mActivity instanceof TriAevumActivity) {
                    ((TriAevumActivity) mActivity).restoreDefaultHudLayout();
                }
            });
        }

        Button btnEditControls = findViewById(R.id.btn_edit_virtual_controls);
        if (btnEditControls != null) {
            btnEditControls.setOnClickListener(v -> {
                dismiss();
                if (mActivity instanceof TriAevumActivity) {
                    ((TriAevumActivity) mActivity).openVirtualControlsEditor();
                }
            });
        }

        Button btnResetControls = findViewById(R.id.btn_reset_virtual_controls);
        if (btnResetControls != null) {
            btnResetControls.setOnClickListener(v -> {
                if (mOverlay != null) {
                    mOverlay.resetControlPositions();
                    Toast.makeText(getContext(), "Control positions restored to default!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (mBtnExportSave != null) {
            mBtnExportSave.setOnClickListener(v -> {
                if (!TriAevumSaveManager.hasSaveFiles(getContext())) {
                    Toast.makeText(getContext(), "No save file recorded yet to export.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (mActivity instanceof TriAevumActivity) {
                    ((TriAevumActivity) mActivity).startExportSaveFlow(() -> {
                        refreshSaveStatus();
                    });
                }
            });
        }

        if (mBtnImportSave != null) {
            mBtnImportSave.setOnClickListener(v -> {
                new android.app.AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Import Save")
                    .setMessage("Importing a new save will overwrite the current progress files in the save folder.\n\nDo you want to continue and select the file (.zip / save)?")
                    .setPositiveButton("Select File", (dialog, which) -> {
                        if (mActivity instanceof TriAevumActivity) {
                            ((TriAevumActivity) mActivity).startImportSaveFlow(() -> {
                                refreshSaveStatus();
                            });
                        }
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
            });
        }

        if (mBtnSelectTexturesDir != null) {
            mBtnSelectTexturesDir.setOnClickListener(v -> {
                if (mActivity instanceof TriAevumActivity) {
                    ((TriAevumActivity) mActivity).startSelectTextureDirFlow(newPath -> {
                        if (mCbCustomTextures != null) {
                            mCbCustomTextures.setChecked(true);
                        }
                        updateTextureDirectoryDisplay();
                    });
                }
            });
        }

        if (mBtnResetTexturesDir != null) {
            mBtnResetTexturesDir.setOnClickListener(v -> {
                mConfig.setCustomTexturesPath("");
                TriAevumConfigManager.nativeReloadGraphicsSettings();
                updateTextureDirectoryDisplay();
                Toast.makeText(getContext(), "Texture directory reset to the app default folder!", Toast.LENGTH_SHORT).show();
            });
        }

        if (mBtnPreloadNow != null) {
            mBtnPreloadNow.setOnClickListener(v -> performManualPreload());
        }

        if (mBtnInstallDriver != null) {
            mBtnInstallDriver.setOnClickListener(v -> {
                if (mActivity instanceof TriAevumActivity) {
                    ((TriAevumActivity) mActivity).startInstallDriverFlow(driver -> {
                        loadGpuDriversList();
                    });
                }
            });
        }

        if (mBtnDeleteDriver != null) {
            mBtnDeleteDriver.setOnClickListener(v -> {
                int pos = mSpGpuDriver.getSelectedItemPosition();
                if (pos <= 0 || pos > mInstalledDrivers.size()) {
                    Toast.makeText(getContext(), "The default system driver cannot be deleted.", Toast.LENGTH_SHORT).show();
                    return;
                }
                TriAevumCustomDriverManager.CustomDriverInfo toDelete = mInstalledDrivers.get(pos - 1);
                new android.app.AlertDialog.Builder(getContext())
                    .setTitle("Delete Driver")
                    .setMessage("Do you really want to delete the driver \"" + toDelete.name + "\"?")
                    .setPositiveButton("Sim", (dialog, which) -> {
                        mCustomDriverManager.deleteDriver(toDelete.id);
                        Toast.makeText(getContext(), "Driver deleted.", Toast.LENGTH_SHORT).show();
                        loadGpuDriversList();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
            });
        }
    }

    private void loadGpuDriversList() {
        if (mSpGpuDriver == null || mCustomDriverManager == null) return;
        mInstalledDrivers = mCustomDriverManager.getInstalledDrivers();
        java.util.List<String> labels = new java.util.ArrayList<>();
        labels.add("📱 System Default (Qualcomm)");
        int selectedIndex = 0;
        String currentSelectedId = mCustomDriverManager.getSelectedDriverId();

        for (int i = 0; i < mInstalledDrivers.size(); i++) {
            TriAevumCustomDriverManager.CustomDriverInfo d = mInstalledDrivers.get(i);
            labels.add("⚡ " + d.name);
            if (d.id.equals(currentSelectedId)) {
                selectedIndex = i + 1;
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpGpuDriver.setAdapter(adapter);
        mSpGpuDriver.setSelection(selectedIndex);

        mSpGpuDriver.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    mTvDriverInfo.setText("📱 System Driver: default Qualcomm Adreno stock.");
                } else if (position - 1 < mInstalledDrivers.size()) {
                    TriAevumCustomDriverManager.CustomDriverInfo d = mInstalledDrivers.get(position - 1);
                    mTvDriverInfo.setText("⚡ " + d.name + "\nAuthor: " + d.author + " | Version: " + d.driverVersion + "\nFile: " + d.libraryName);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void performManualPreload() {
        String path = mConfig.getCustomTexturesPath();
        java.io.File dir;
        if (path != null && !path.trim().isEmpty()) {
            dir = new java.io.File(path);
        } else {
            dir = TriAevumTextureManager.getDefaultTexturesDir(getContext());
        }

        if (!dir.exists() || !dir.isDirectory()) {
            Toast.makeText(getContext(), "Texture folder not found: " + dir.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            return;
        }

        android.app.ProgressDialog progress = new android.app.ProgressDialog(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert);
        progress.setTitle("⚡ Preloading Textures");
        progress.setMessage("Indexing and loading textures into memory...");
        progress.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.setCancelable(false);
        progress.show();

        new Thread(() -> {
            try {
                int loaded = TriAevumActivity.nativePreloadCustomTextures(dir.getAbsolutePath(), (current, total) -> {
                    int percent = total > 0 ? (int)((current * 100L) / total) : 0;
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        progress.setProgress(percent);
                        progress.setMessage(String.format(java.util.Locale.getDefault(), "%d / %d textures (%d%%)", current, total, percent));
                    });
                });

                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    progress.dismiss();
                    Toast.makeText(getContext(), "✅ " + loaded + " textures preloaded into memory successfully!", Toast.LENGTH_LONG).show();
                    updateTextureDirectoryDisplay();
                });
            } catch (Throwable t) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    progress.dismiss();
                    Toast.makeText(getContext(), "Error preloading: " + t.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void updateTextureDirectoryDisplay() {
        if (mTvCustomTexturesPath == null) return;
        String path = mConfig.getCustomTexturesPath();
        if (path == null || path.trim().isEmpty()) {
            java.io.File defaultDir = TriAevumTextureManager.getDefaultTexturesDir(getContext());
            mTvCustomTexturesPath.setText("Directory: Default (" + defaultDir.getAbsolutePath() + ")");
            mTvCustomTexturesCount.setText("Checking textures...");
            TriAevumTextureManager.countTexturesAsync(defaultDir.getAbsolutePath(), (count, summary) -> {
                if (mTvCustomTexturesCount != null) {
                    mTvCustomTexturesCount.setText(summary);
                }
            });
        } else {
            mTvCustomTexturesPath.setText("Directory: " + path);
            mTvCustomTexturesCount.setText("Checking textures...");
            TriAevumTextureManager.countTexturesAsync(path, (count, summary) -> {
                if (mTvCustomTexturesCount != null) {
                    mTvCustomTexturesCount.setText(summary);
                }
            });
        }
    }

    // -------------------------------------------------------------------------
    // Save
    // -------------------------------------------------------------------------

    private synchronized void saveToConfig() {
        if (mSaving) return;
        mSaving = true;
        try {
            Log.d(TAG, "--- saveToConfig START ---");
            // Geral
            int langIdx = mSpLanguage.getSelectedItemPosition();
            if (langIdx >= 0 && langIdx < TriAevumConfigManager.LANGUAGE_CODES.length) {
                String lang = TriAevumConfigManager.LANGUAGE_CODES[langIdx];
                Log.d(TAG, "  SET language -> " + lang);
                mConfig.setLanguageCode(lang);
            }

            int resIdx = mSpSurfaceRes.getSelectedItemPosition();
            if (resIdx >= 0 && resIdx < TriAevumConfigManager.SURFACE_RES_VALUES.length) {
                int edge = TriAevumConfigManager.SURFACE_RES_VALUES[resIdx];
                Log.d(TAG, "  SET surfaceMaxShortEdge -> " + edge);
                mConfig.setSurfaceMaxShortEdge(edge);
                if (mActivity instanceof TriAevumActivity) {
                    ((TriAevumActivity) mActivity).updateSurfaceResolution(edge);
                }
            }

        // Gráficos
        int rsIdx = mSpRenderScale.getSelectedItemPosition();
        float rs = (rsIdx >= 0 && rsIdx < TriAevumConfigManager.RENDER_SCALE_VALUES.length)
                ? TriAevumConfigManager.RENDER_SCALE_VALUES[rsIdx]
                : 1.0f;

        int aaIdx = mSpAAMode.getSelectedItemPosition();
        String aa = (aaIdx >= 0 && aaIdx < TriAevumConfigManager.AA_MODE_VALUES.length)
                ? TriAevumConfigManager.AA_MODE_VALUES[aaIdx]
                : "Off";

        int frIdx = mSpFramerate.getSelectedItemPosition();
        String fr = (frIdx >= 0 && frIdx < TriAevumConfigManager.FRAMERATE_VALUES.length)
                ? TriAevumConfigManager.FRAMERATE_VALUES[frIdx]
                : "Original30";

        boolean vsync = mCbVSync.isChecked();
        if (mCbShowFps != null) {
            mActivity.getApplicationContext().getSharedPreferences("org.triaevum.android_preferences", Context.MODE_PRIVATE)
                .edit().putBoolean("show_game_fps_counter", mCbShowFps.isChecked()).apply();
            if (mActivity instanceof TriAevumActivity) {
                ((TriAevumActivity) mActivity).updateFpsCounterVisibility();
            }
        }
        boolean customTextures = mCbCustomTextures.isChecked();

        Log.d(TAG, "  SET graphics batch -> scale=" + rs + ", aa=" + aa + ", fr=" + fr + ", vsync=" + vsync + ", customTextures=" + customTextures);
        mConfig.saveGraphicsSettings(rs, aa, fr, vsync, customTextures);
        if (mActivity instanceof TriAevumActivity) {
            ((TriAevumActivity) mActivity).updateDisplayRefreshRate(fr);
        }
        if (mCbPreloadTextures != null) {
            mConfig.setCustomTexturesPreloadEnabled(mCbPreloadTextures.isChecked());
        }

        // Visual Mods
        boolean toonEnabled = mCbToonShading.isChecked();
        boolean toonOutline = mCbToonOutline.isChecked();
        int grassIdx = mSpGrassQuality.getSelectedItemPosition();
        String grassQuality = (grassIdx >= 0 && grassIdx < TriAevumConfigManager.GRASS_QUALITY_VALUES.length)
                ? TriAevumConfigManager.GRASS_QUALITY_VALUES[grassIdx]
                : "Off";
        float fovVal = 1.0f + (mSbFov.getProgress() * 0.05f);
        Log.d(TAG, "  SET visual mods -> toon=" + toonEnabled + ", outline=" + toonOutline + ", grass=" + grassQuality + ", fov=" + fovVal);
        mConfig.saveVisualMods(toonEnabled, toonOutline, grassQuality, fovVal);

        // Câmera / HUD
        Log.d(TAG, "  SET freeCamera -> " + mCbFreeCamera.isChecked());
        mConfig.setFreeCameraEnabled(mCbFreeCamera.isChecked());
        Log.d(TAG, "  SET camSpeedLevel -> " + (mSbCamSpeed.getProgress() + 1));
        mConfig.setFreeCameraSpeedLevel(mSbCamSpeed.getProgress() + 1);
        mConfig.setFreeCameraInvertX(mCbCamInvertX.isChecked());
        mConfig.setFreeCameraInvertY(mCbCamInvertY.isChecked());
        int hudLayoutIdx = mSpHudLayout.getSelectedItemPosition();
        if (hudLayoutIdx >= 0 && hudLayoutIdx < TriAevumConfigManager.HUD_LAYOUT_VALUES.length) {
            String hl = TriAevumConfigManager.HUD_LAYOUT_VALUES[hudLayoutIdx];
            Log.d(TAG, "  SET hudLayout -> " + hl);
            mConfig.setHudLayout(hl);
        }

        float hudScale = 0.5f + mSbHudScale.getProgress() * 0.1f;
        Log.d(TAG, "  SET hudScale -> " + hudScale);
        mConfig.setHudScale(hudScale);

        int mx = mSbHudMarginX.getProgress() - 3;
        int my = mSbHudMarginY.getProgress() - 3;
        Log.d(TAG, "  SET hudMarginX -> " + mx + ", hudMarginY -> " + my);
        mConfig.setHudMarginX(mx);
        mConfig.setHudMarginY(my);

        mConfig.setMinimapVisible(mCbMinimap.isChecked());
        mConfig.setRenderDpadIcons(mCbDpadIcons.isChecked());
        mConfig.setRenderItemsHint(mCbItemsHint.isChecked());

        // Controles — write directly to SharedPreferences (same keys as EmulationMenuSettings)
        boolean showOverlay = mCbShowOverlay.isChecked();
        boolean haptic = mCbHaptic.isChecked();
        boolean swapScr = mCbSwapScreens.isChecked();
        boolean touchScreen = mCbTouchScreen.isChecked();
        boolean joyRel = mCbJoystickRelCenter.isChecked();
        boolean dpadSlide = mCbDpadSlide.isChecked();

        Log.d(TAG, "  saving controls: showOverlay=" + showOverlay
            + " haptic=" + haptic
            + " swapScreens=" + swapScr
            + " touchScreen=" + touchScreen
            + " joystickRelCenter=" + joyRel
            + " dpadSlide=" + dpadSlide);
        mPrefs.edit()
            .putBoolean("EmulationMenuSettings_ShowOverlay", showOverlay)
            .putBoolean("EmulationMenuSettings_HapticFeedback", haptic)
            .putBoolean("EmulationMenuSettings_SwapScreens", swapScr)
            .putBoolean(MotionSensorBridge.PREF_KEY, mCbGyro.isChecked())
            .putInt(RenderTuning.PREF_KEY, mSpInterp.getSelectedItemPosition())
            .putBoolean("EmulationMenuSettings_TouchEnabled", touchScreen)
            .putBoolean("EmulationMenuSettings_JoystickRelCenter", joyRel)
            .putBoolean("EmulationMenuSettings_DpadSlideEnable", dpadSlide)
            .apply();

        if (mOverlay != null) {
            mOverlay.setShowControls(showOverlay);
            mOverlay.setHapticFeedbackEnabled(haptic);
            mOverlay.setTouchEnabled(touchScreen);
            mOverlay.setOverlayOpacityPercent(mSbOpacity.getProgress());
        }
        try {
            AndroidNativeInputTarget.nativeSwapScreens(swapScr);
            AndroidNativeInputTarget.nativeSetTouchEnabled(touchScreen);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to apply native control settings", t);
        }
        MotionSensorBridge.refresh();
        RenderTuning.applyFromPrefs(getContext());

        // Driver de GPU customizado (Turnip/Vulkan)
        if (mSpGpuDriver != null && mCustomDriverManager != null) {
            int drvPos = mSpGpuDriver.getSelectedItemPosition();
            String prevDriver = mCustomDriverManager.getSelectedDriverId();
            String newDriver = "";
            if (drvPos > 0 && drvPos <= mInstalledDrivers.size()) {
                newDriver = mInstalledDrivers.get(drvPos - 1).id;
            }
            if (!prevDriver.equals(newDriver)) {
                mCustomDriverManager.setSelectedDriverId(newDriver);
                Toast.makeText(getContext(), "GPU driver changed! Restart the game to apply the new driver.", Toast.LENGTH_LONG).show();
            }
        }
        Log.d(TAG, "--- saveToConfig END ---");
        } finally {
            mSaving = false;
        }
    }

    // -------------------------------------------------------------------------
    // Spinner helper
    // -------------------------------------------------------------------------

    private void setupSpinner(Spinner spinner, String[] items, Integer selectedIndex) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
            getContext(), android.R.layout.simple_spinner_item, items) {
            @Override
            public View getView(int pos, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(pos, convertView, parent);
                tv.setTextColor(Color.parseColor("#C8D8E8"));
                tv.setTextSize(12f);
                return tv;
            }
            @Override
            public View getDropDownView(int pos, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(pos, convertView, parent);
                tv.setTextColor(Color.parseColor("#FFD700"));
                tv.setBackgroundColor(Color.parseColor("#1A212B"));
                tv.setPadding(24, 16, 24, 16);
                tv.setTextSize(12f);
                return tv;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        if (selectedIndex != null) spinner.setSelection(selectedIndex);
    }
}

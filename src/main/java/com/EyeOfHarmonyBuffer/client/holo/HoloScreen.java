package com.EyeOfHarmonyBuffer.client.holo;

import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * 世界全息屏抽象基类。一块屏 = 一个实例：
 * 独立状态（子类字段）+ widget 列表 + 层栈 + 焦点 + 交互回调。
 * 渲染/交互框架只调本类方法，不关心具体屏类型 —— 加新屏不需要改框架。
 *
 * 继承约定：
 * - buildWidgets()    构建控件列表
 * - drawBackground()  画底层内容（背景/静态文字/栅格等）
 * - drawOverlay()     可选，控件之上叠加（状态文字/边框提示）
 * - onMouse()/onClose() 覆写交互回调（onMouse 默认空，需要响应的屏自己实现）
 */
public abstract class HoloScreen {

    /** 屏宽高（局部像素；世界渲染时按 HoloRender.SCALE 缩放）。 */
    public final int w, h;

    protected final List<HoloWidget> widgets = new ArrayList<>();
    private final Deque<List<HoloWidget>> backStack = new ArrayDeque<>();
    private HoloWidget focus;

    /** 关闭回调：屏内 requestClose() 时触发（一般指向关闭宿主实体）。 */
    private Runnable closeRequest;

    protected HoloScreen(int w, int h) {
        this.w = w;
        this.h = h;
        buildWidgets();
    }

    /** 子类在此构建控件列表。 */
    protected abstract void buildWidgets();

    /** 子类绘制背景（画布上画底层内容）。 */
    protected abstract void drawBackground(HoloCanvas c);

    /** 子类可选：在控件之上叠加绘制（如状态文字、边框提示）。 */
    protected void drawOverlay(HoloCanvas c) {}

    // ==================== 控件容器（原 RbmkHoloPanel 逻辑） ====================

    /** 命中：按 z 从高到低找最上层命中的控件（重叠时上层优先）。 */
    public HoloWidget hitAt(int px, int py) {
        HoloWidget top = null;
        for (HoloWidget c : widgets) {
            if (c.contains(px, py) && (top == null || c.z > top.z)) {
                top = c;
            }
        }
        return top;
    }

    /** 更新各控件 hover 状态（准星在屏上时，命中最上层控件）。 */
    public void updateHover(int px, int py, boolean hovering) {
        HoloWidget hit = hovering ? hitAt(px, py) : null;
        for (HoloWidget c : widgets) {
            c.hovered = (c == hit);
        }
    }

    /** 绘制整屏：背景 → 控件（z 升序）→ 叠加层。
     *
     * 背景与内容同处一个 z=0 平面；开启深度测试后，GPU 对同一平面上两个三角形的
     * 逐像素深度插值可能差 1 ULP，导致内容被背景深度剔除（露出黑色背景 → 整面黑色
     * 条纹/闪烁，且图案随屏朝向变化）。处理：
     * - 内容层向相机方向做多边形偏移（比背景更近，必然通过背景的深度测试）；
     * - 内容层关闭深度写入（不再与后续内容互相剔除）。
     * 方块遮挡不受影响：方块深度比屏近得多，仍正常挡住整块屏。 */
    public void draw(HoloCanvas c) {
        drawBackground(c);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(-2.0f, -2.0f);
        GL11.glDepthMask(false);
        List<HoloWidget> sorted = new ArrayList<>(widgets);
        sorted.sort(Comparator.comparingInt(w2 -> w2.z));
        for (HoloWidget w2 : sorted) {
            w2.draw(c);
        }
        drawOverlay(c);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(0, 0);
    }

    // ---- 焦点 ----

    public boolean hasFocus() {
        return focus != null;
    }

    /** 请求聚焦；仅可聚焦控件会获得焦点，其他点击清空焦点。 */
    public void requestFocus(HoloWidget c) {
        if (focus != null && focus != c) {
            focus.onFocusLost();
        }
        focus = (c != null && c.isFocusable()) ? c : null;
    }

    public void clearFocus() {
        requestFocus(null);
    }

    /** 键盘路由给聚焦控件。返回 true 表示该键已被消费。 */
    public boolean handleKey(char c, int key) {
        return focus != null && focus.onKey(c, key);
    }

    // ---- 层栈（右键退出） ----

    /** 进入子层：保存当前层，切换为子层控件。 */
    public void pushLayer(List<HoloWidget> layer) {
        backStack.push(new ArrayList<>(widgets));
        widgets.clear();
        widgets.addAll(layer);
        clearFocus();
    }

    /** 弹回上一层。返回 false 表示没有上层（调用方应关闭屏）。 */
    public boolean goBack() {
        if (backStack.isEmpty()) {
            return false;
        }
        widgets.clear();
        widgets.addAll(backStack.pop());
        clearFocus();
        return true;
    }

    // ==================== 交互回调（由 HoloInteraction 调用） ====================

    /** 是否已激活：未激活时左键"进入"，激活后左键操作、右键退出（所有屏共用的基本交互模型）。 */
    protected boolean activated = false;

    /** 每 tick 准星悬停：未激活时不亮控件（防误触），激活后正常更新控件 hover。
     *  子类需额外记录悬停坐标时可覆写并调用 super。 */
    public void onHover(int px, int py, boolean hovering) {
        updateHover(activated ? px : 0, activated ? py : 0, activated && hovering);
    }

    /** 鼠标按键（0=左键 1=右键），u/v 为局部坐标。
     *  基本交互模型：未激活时左键进入；激活后左键操作、右键退出。子类不必重复写。 */
    public void onMouse(int button, int u, int v) {
        if (button == 1) {
            if (activated) {
                onRightClick();
            }
        } else if (button == 0) {
            if (!activated) {
                activate();
            } else {
                onLeftClick(u, v);
            }
        }
    }

    /** 左键进入：置激活态。子类可覆写做进入动作（如复位选中）。 */
    protected void activate() {
        activated = true;
    }

    /** 激活后的左键操作：默认命中并点击控件（点击坐标随事件传给控件）。子类可覆写（如格子选中）。 */
    protected void onLeftClick(int u, int v) {
        HoloWidget c = hitAt(u, v);
        requestFocus(c);
        if (c != null) {
            c.onClick(u, v);
        }
    }

    /** 激活后的右键退出路径：清焦点 → 弹层 → 复位激活态并请求关闭。
     *  子类可覆写（如先取消固定再退出）。 */
    protected void onRightClick() {
        if (hasFocus()) {
            clearFocus();
            return;
        }
        if (goBack()) {
            return;
        }
        activated = false;
        requestClose();
    }

    /** 屏被关闭前回调（默认清焦点并复位激活态）。 */
    public void onClose() {
        clearFocus();
        activated = false;
    }

    /** 请求关闭本屏：触发宿主关闭对应实体。 */
    protected final void requestClose() {
        if (closeRequest != null) {
            closeRequest.run();
        }
    }

    /** 由宿主（HoloEntity）设置关闭回调。 */
    public final void setCloseRequest(Runnable r) {
        this.closeRequest = r;
    }
}

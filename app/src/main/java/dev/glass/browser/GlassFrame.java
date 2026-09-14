package dev.glass.browser;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.FrameLayout;

/**
 * Native floating phone chrome with corner grips, top drag pill, and bottom gesture bar.
 */
final class GlassFrame extends FrameLayout {
    interface Gestures {
        void start(int region, float x, float y);
        void move(float x, float y);
        void end(float x, float y, boolean cancel);
    }

    static final int MOVE = 1, TL = 2, TR = 3, BL = 4, BR = 5, BAR = 6;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final float density;
    private final Gestures gestures;
    private int active, pointer = -1;

    GlassFrame(Context context, Gestures gestures) {
        super(context);
        this.gestures = gestures;
        this.density = getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);
    }

    private float d(float n) { return density * n; }

    private int hit(float x, float y) {
        float c = d(36), w = getWidth(), h = getHeight();
        if (x < c && y < c) return TL;
        if (x > w - c && y < c) return TR;
        if (x < c && y > h - c) return BL;
        if (x > w - c && y > h - c) return BR;
        if (y < d(24)) return MOVE; // Top notch / drag handle area
        if (y > h - d(28)) return BAR;  // Bottom gesture bar area
        return 0;
    }

    private Runnable inside, outside;
    void setFocusCallbacks(Runnable inside, Runnable outside) {
        this.inside = inside;
        this.outside = outside;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (hit(e.getX(), e.getY()) == 0 && inside != null) inside.run();
            else if (outside != null) outside.run();
        }
        if (e.getActionMasked() == MotionEvent.ACTION_OUTSIDE && outside != null) {
            outside.run();
            return true;
        }
        return super.dispatchTouchEvent(e);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            active = hit(e.getX(), e.getY());
            pointer = e.getPointerId(0);
            if (active != 0) {
                gestures.start(active, e.getRawX(), e.getRawY());
                return true;
            }
        }
        return active != 0;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (active == 0) return super.onTouchEvent(e);
        if (e.getPointerId(e.getActionIndex()) != pointer && e.getActionMasked() == MotionEvent.ACTION_POINTER_UP) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (e.getPointerId(0) == pointer) gestures.move(e.getRawX(), e.getRawY());
                break;
            case MotionEvent.ACTION_UP:
                gestures.end(e.getRawX(), e.getRawY(), false);
                active = 0;
                pointer = -1;
                performClick();
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_UP:
                gestures.end(e.getRawX(), e.getRawY(), true);
                active = 0;
                pointer = -1;
                break;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight(), r = d(28), edge = d(1);
        RectF outer = new RectF(edge, edge, w - edge, h - edge);
        clip.reset();
        clip.addRoundRect(outer, r, r, Path.Direction.CW);

        int save = canvas.save();
        canvas.clipPath(clip);

        // Dark modern frame background tint (very subtle dark glass so text is readable over video)
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(0x1810151C);
        canvas.drawRoundRect(outer, r, r, paint);

        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);

        // Sleek outer stroke matching left screenshot
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0x408090A0);
        paint.setStrokeWidth(d(1.2f));
        canvas.drawRoundRect(outer, r, r, paint);

        // Corner resize grips
        paint.setColor(0x99D0E0F0);
        paint.setStrokeWidth(d(2.2f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        float m = d(7), s = d(30);
        canvas.drawArc(new RectF(m, m, s, s), 190, 65, false, paint);
        canvas.drawArc(new RectF(w - s, m, w - m, s), 285, 65, false, paint);
        canvas.drawArc(new RectF(m, h - s, s, h - m), 100, 65, false, paint);
        canvas.drawArc(new RectF(w - s, h - s, w - m, h - m), 10, 65, false, paint);

        // Top speaker / notch camera dots (matching phone mockup on left)
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x88A0B0C0);
        float cy = d(11);
        canvas.drawCircle(w / 2 - d(10), cy, d(1.8f), paint);
        canvas.drawCircle(w / 2, cy, d(2.2f), paint);
        canvas.drawCircle(w / 2 + d(10), cy, d(1.8f), paint);

        // Bottom gesture pill bar
        paint.setColor(0x33000000);
        canvas.drawRoundRect(new RectF(w / 2 - d(42), h - d(15), w / 2 + d(42), h - d(9)), d(3), d(3), paint);
        paint.setColor(0xDDE0E8F0);
        canvas.drawRoundRect(new RectF(w / 2 - d(40), h - d(14), w / 2 + d(40), h - d(10)), d(2), d(2), paint);
    }
}

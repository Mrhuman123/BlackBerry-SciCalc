package net.mbeffects.calc;

import net.rim.device.api.system.Display;
import net.rim.device.api.ui.*;
import net.rim.device.api.ui.component.*;
import net.rim.device.api.ui.container.*;
import net.rim.device.api.ui.decor.BackgroundFactory;

// ============================================================
//  CalcMainScreen.java  —  MBEffects Scientific Calculator :-)
// ============================================================
//
//  TOUCH FIX — root cause & solution
//  -----------------------------------
//  GridFieldManager does NOT forward touch events to child Fields
//  on all BB OS 6 firmware builds.  The fix is to replace the
//  grid with a custom Manager that:
//   1. Lays children out manually using absolute x/y positions.
//   2. Overrides touchEvent() in the Manager itself to find which
//      button was hit by x,y coordinate and call its action.
//  This bypasses the broken event-routing in GridFieldManager
//  entirely and works on every BB OS 6/7 device.
//
//  Three input paths — every button responds to all three:
//   A. Touch screen  → Manager.touchEvent() hit-tests x,y
//   B. Trackpad      → CalcButton.navigationClick()
//   C. Keyboard      → CalcButton.keyChar() on ENTER / SPACE
//
//  COMPILE ERRORS FIXED
//  ---------------------
//  • "Cannot override final method from Screen" — the method
//    was renamed to refreshDisp() so it never clashes with
//    Screen.updateDisplay() which is final in BB API.
//  • "Characters cannot be resolved" — Characters class removed;
//    char literals '\n' and ' ' used instead.
//  • Math.asin/acos/atan missing in CLDC — handled in MathEngine
//    using MathUtilities.atan2 identities.
//
//  PALETTE — Dark Gold Luxury
//  ---------------------------
//   #1A1510  main background
//   #110E0A  display panel
//   #2E251E  button normal
//   #3D3126  button pressed
//   #261C12  operator buttons
//   #4A1C0A  C / DEL buttons
//   #B8960A  equals button
//   #F5ECD7  text cream
//   #FFD700  gold accent
//   #4F3E2E  border
//
//  Target: BlackBerry OS 6.0+ (CLDC 1.1 / MIDP 2.0)
//  Author: mbeffects (Mohamed BOURI)
// ============================================================
public class CalcMainScreen extends MainScreen implements FieldChangeListener {

    // Layout
    private static final int COLS       = 5;
    private static final int ROWS       = 7;
    private static final int DISPLAY_H  = 64;

    // Palette
    private static final int C_BG        = 0x001A1510;
    private static final int C_DISP_BG   = 0x00110E0A;
    private static final int C_BTN       = 0x002E251E;
    private static final int C_BTN_DOWN  = 0x003D3126;
    private static final int C_BTN_OP    = 0x00261C12;
    private static final int C_BTN_CLR   = 0x004A1C0A;
    private static final int C_BTN_EQ    = 0x00B8960A;
    private static final int C_TEXT      = 0x00F5ECD7;
    private static final int C_GOLD      = 0x00FFD700;
    private static final int C_BORDER    = 0x004F3E2E;
    private static final int C_RED       = 0x00FF7744;

    // Max digits the user may type (operators/functions not counted)
    private static final int MAX_DIGITS = 10;

    // State
    private LabelField _disp;
    private String     _expr        = "";
    private double     _lastResult  = 0.0;
    private boolean    _resultShown = false;

    // Button grid reference
    private ButtonGrid _grid;

    // ----------------------------------------------------------------
    public CalcMainScreen() {
        super(NO_VERTICAL_SCROLL);
        getMainManager().setBackground(
            BackgroundFactory.createSolidBackground(C_BG));
        buildDisplayPanel();
        buildButtonGrid();
    }

    // ----------------------------------------------------------------
    //  Display panel
    // ----------------------------------------------------------------
    private void buildDisplayPanel() {
        HorizontalFieldManager panel =
            new HorizontalFieldManager(USE_ALL_WIDTH) {
                protected void paint(Graphics g) {
                    g.setColor(C_DISP_BG);
                    g.fillRect(0, 0, getWidth(), getHeight());
                    // gold rule at bottom
                    g.setColor(C_GOLD);
                    g.drawLine(0, getHeight()-2, getWidth(), getHeight()-2);
                    g.setColor(C_BORDER);
                    g.drawLine(0, getHeight()-1, getWidth(), getHeight()-1);
                    super.paint(g);
                }
                public int getPreferredHeight() { return DISPLAY_H; }
                protected void sublayout(int mw, int mh) {
                    super.sublayout(mw, DISPLAY_H);
                    setExtent(mw, DISPLAY_H);
                }
            };

        _disp = new LabelField("0", FIELD_RIGHT | USE_ALL_WIDTH) {
            protected void paint(Graphics g) {
                g.setColor(C_TEXT);
                super.paint(g);
            }
        };
        _disp.setFont(Font.getDefault().derive(Font.BOLD, 38));
        _disp.setPadding(10, 10, 0, 0);
        panel.add(_disp);
        add(panel);
    }

    // ----------------------------------------------------------------
    //  Button grid  — uses custom ButtonGrid Manager, NOT GridFieldManager
    // ----------------------------------------------------------------
    private static final String[] KEYS = {
        "sin",  "cos",  "tan",  "log",  "ln",
        "asin", "acos", "atan", "sqrt", "^",
        "(",    ")",    "C",    "DEL",  "/",
        "7",    "8",    "9",    "*",    "!",
        "4",    "5",    "6",    "-",    "e",
        "1",    "2",    "3",    "+",    "pi",
        "0",    ".",    "E",    "ANS",  "="
    };

    private void buildButtonGrid() {
        _grid = new ButtonGrid();
        for (int i = 0; i < KEYS.length; i++) {
            CalcButton btn = new CalcButton(KEYS[i]);
            btn.setChangeListener(this);
            _grid.add(btn);
        }
        add(_grid);
    }

    // ----------------------------------------------------------------
    //  FieldChangeListener
    // ----------------------------------------------------------------
    public void fieldChanged(Field field, int context) {
        if (field instanceof CalcButton) {
            processKey(((CalcButton) field).getKey());
        }
    }

    // ----------------------------------------------------------------
    //  Key logic
    // ----------------------------------------------------------------
    private void processKey(String key) {

        if (key.equals("C")) {
            _expr        = "";
            _resultShown = false;

        } else if (key.equals("DEL")) {
            if (_expr.length() > 0)
                _expr = _expr.substring(0, _expr.length() - 1);
            _resultShown = false;

        } else if (key.equals("=")) {
            if (_expr.length() == 0) return;
            try {
                double res   = MathEngine.eval(_expr);
                _lastResult  = res;
                _expr        = fmtResult(res);
                _resultShown = true;
            } catch (Exception e) {
                _expr        = "Error";
                _resultShown = false;
            }

        } else {
            // After a result, operators chain from it; anything else starts fresh
            if (_resultShown) {
                boolean isOp = key.equals("+") || key.equals("-")
                            || key.equals("*") || key.equals("/")
                            || key.equals("^") || key.equals("!");
                if (!isOp) _expr = "";
                _resultShown = false;
            }

            // ── 10-digit cap on raw digit / decimal / E entry ────────────
            // Count how many digit/dot/E characters are already in _expr
            boolean isDigitKey = (key.length() == 1)
                && (key.charAt(0) >= '0' && key.charAt(0) <= '9'
                    || key.charAt(0) == '.' || key.charAt(0) == 'E');
            if (isDigitKey && countDigits(_expr) >= MAX_DIGITS) {
                refreshDisp(); // just redraw, don't append
                return;
            }

            // ── Append to expression ─────────────────────────────────────
            if (key.equals("ANS")) {
                _expr += fmtResult(_lastResult);
            } else if (key.equals("pi")) {
                _expr += "3.14159265358979";
            } else if (key.equals("e")) {
                _expr += "2.71828182845905";
            } else if (key.equals("sin")  || key.equals("cos")  || key.equals("tan")
                    || key.equals("asin") || key.equals("acos") || key.equals("atan")
                    || key.equals("log")  || key.equals("ln")   || key.equals("sqrt")) {
                _expr += key + "(";
            } else {
                _expr += key;
            }
        }

        refreshDisp();
    }

    /** Count digit/dot/E characters in expression (used for digit cap). */
    private int countDigits(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= '0' && c <= '9') || c == '.' || c == 'E') count++;
        }
        return count;
    }

    /**
     * Format a result number for display.
     * - Whole numbers: no decimal  (42 not 42.0)
     * - Truncate to 10 visible characters to fit the display
     */
    private String fmtResult(double v) {
        String s;
        if (Double.isInfinite(v)) return v > 0 ? "Inf" : "-Inf";
        if (Double.isNaN(v))      return "NaN";
        if (v == Math.floor(v) && Math.abs(v) < 1.0e10) {
            s = String.valueOf((long) v);
        } else {
            s = String.valueOf(v);
        }
        // Hard cap: never show more than 10 chars on the display
        if (s.length() > 10) s = s.substring(0, 10);
        return s;
    }

    // RENAMED: must NOT be "updateDisplay" — Screen.updateDisplay() is final
    private void refreshDisp() {
        String text = _expr.length() == 0 ? "0" : _expr;
        // Display cap: if expression itself is very long (e.g. after ANS insert),
        // show only the last 10 characters so it never overflows the label
        if (text.length() > 10 && !text.equals("Error")) {
            text = text.substring(text.length() - 10);
        }
        _disp.setText(text);
    }

    // ================================================================
    //  ButtonGrid  — custom Manager that owns touch routing
    //
    //  This is the core fix for "touch not working on grid buttons".
    //  GridFieldManager swallows touch events on many BB OS 6 builds.
    //  ButtonGrid overrides touchEvent() at the Manager level, does
    //  an x,y hit-test to find which CalcButton was touched, and
    //  calls that button's onTouched() method directly — no event
    //  routing through the framework needed.
    // ================================================================
    private final class ButtonGrid extends Manager {

        private final int _btnW;
        private final int _btnH;

        ButtonGrid() {
            super(USE_ALL_WIDTH);
            _btnW = Display.getWidth()  / COLS;
            _btnH = (Display.getHeight() - DISPLAY_H) / ROWS;
        }

        // Layout: place each button at absolute (col*btnW, row*btnH)
        protected void sublayout(int maxW, int maxH) {
            int count = getFieldCount();
            for (int i = 0; i < count; i++) {
                Field f = getField(i);
                int col = i % COLS;
                int row = i / COLS;
                layoutChild(f, _btnW, _btnH);
                setPositionChild(f, col * _btnW, row * _btnH);
            }
            setExtent(maxW, _btnH * ROWS);
        }

        public int getPreferredWidth()  { return Display.getWidth(); }
        public int getPreferredHeight() { return _btnH * ROWS; }

        // ── Touch routing — the key fix ────────────────────────────────
        //  Instead of relying on the framework to route to child Fields
        //  (which is unreliable on BB OS 6), we catch every touch event
        //  here at the Manager level and dispatch manually by coordinate.
        protected boolean touchEvent(TouchEvent msg) {
            int event = msg.getEvent();

            // We only care about DOWN, UP, MOVE, CANCEL
            if (event != TouchEvent.DOWN && event != TouchEvent.UP
             && event != TouchEvent.MOVE && event != TouchEvent.CANCEL) {
                return false;
            }

            // Get finger position relative to this Manager
            int x = (int) msg.getX(1);
            int y = (int) msg.getY(1);

            // Hit-test: which button?
            int col = x / _btnW;
            int row = y / _btnH;
            if (col < 0 || col >= COLS || row < 0 || row >= ROWS) return false;

            int idx = row * COLS + col;
            if (idx < 0 || idx >= getFieldCount()) return false;

            Field f = getField(idx);
            if (!(f instanceof CalcButton)) return false;

            CalcButton btn = (CalcButton) f;

            switch (event) {
                case TouchEvent.DOWN:
                    btn.setPressed(true);
                    return true;

                case TouchEvent.UP:
                    btn.setPressed(false);
                    btn.fire();        // ← fires fieldChangeNotify
                    return true;

                case TouchEvent.MOVE:
                    // Check if still inside the same button
                    int newCol = x / _btnW;
                    int newRow = y / _btnH;
                    if (newCol != col || newRow != row) {
                        btn.setPressed(false); // finger left button
                    }
                    return true;

                case TouchEvent.CANCEL:
                    btn.setPressed(false);
                    return true;
            }
            return false;
        }
    }

    // ================================================================
    //  CalcButton
    //  Responds to: touch (via ButtonGrid.touchEvent above),
    //               trackpad (navigationClick), keyboard (keyChar)
    // ================================================================
    private final class CalcButton extends Field {

        private final String  _key;
        private       boolean _pressed = false;
        private final int     _btnW;
        private final int     _btnH;

        CalcButton(String key) {
            super(FOCUSABLE);
            _key  = key;
            _btnW = Display.getWidth()  / COLS;
            _btnH = (Display.getHeight() - DISPLAY_H) / ROWS;
        }

        String getKey() { return _key; }

        // Called by ButtonGrid to update visual state + repaint
        void setPressed(boolean p) {
            if (_pressed != p) {
                _pressed = p;
                invalidate();
            }
        }

        // Called by ButtonGrid on TouchEvent.UP to fire the action
        void fire() {
            fieldChangeNotify(0);
        }

        // ── Layout ────────────────────────────────────────────────────
        public int getPreferredWidth()  { return _btnW; }
        public int getPreferredHeight() { return _btnH; }
        protected void layout(int w, int h) { setExtent(_btnW, _btnH); }

        // ── Paint ──────────────────────────────────────────────────────
        protected void paint(Graphics g) {
            final int W = getWidth();
            final int H = getHeight();

            // background
            g.setColor(_pressed ? C_BTN_DOWN : normalBg());
            g.fillRect(1, 1, W - 2, H - 2);

            // top bevel when not pressed
            if (!_pressed) {
                g.setColor(0x00443828);
                g.drawLine(2, 1, W - 3, 1);
            }

            // border — single dark line always, no yellow focus square
            g.setColor(C_BORDER);
            g.drawRect(0, 0, W-1, H-1);

            // label — slightly sunk when pressed
            Font f  = getFont().derive(Font.PLAIN, fontSize());
            int  tw = f.getAdvance(_key);
            int  th = f.getHeight();
            int  tx = (W - tw) / 2;
            int  ty = (H - th) / 2 + (_pressed ? 1 : 0);
            g.setFont(f);
            g.setColor(labelColor());
            g.drawText(_key, tx, ty);
        }

        private int fontSize() {
            int len = _key.length();
            if (len >= 4) return 13;
            if (len == 3) return 15;
            return 20;
        }

        private int normalBg() {
            if (_key.equals("="))                        return C_BTN_EQ;
            if (_key.equals("C") || _key.equals("DEL")) return C_BTN_CLR;
            if (_key.equals("+") || _key.equals("-")
             || _key.equals("*") || _key.equals("/")
             || _key.equals("^") || _key.equals("!"))   return C_BTN_OP;
            return C_BTN;
        }

        private int labelColor() {
            if (_key.equals("="))                        return 0x00111111;
            if (_key.equals("C") || _key.equals("DEL")) return C_RED;
            if (_key.equals("+") || _key.equals("-")
             || _key.equals("*") || _key.equals("/")
             || _key.equals("^") || _key.equals("!"))   return C_GOLD;
            return C_TEXT;
        }

       

        // ── INPUT: Physical keyboard (ENTER or SPACE) ─────────────────
        // NOTE: no import of Characters class — use char literals only ;-)
        protected boolean keyChar(char key, int status, int time) {
            if (key == '\n' || key == ' ') {
                fire();
                return true;
            }
            return super.keyChar(key, status, time);
        }
// i removed this -
        // ── Focus repaint — not needed, no focus border drawn ──────────
        protected void onFocus(int direction) {
            super.onFocus(direction);
            // intentionally no invalidate() — no focus border to draw
        }

        protected void onUnfocus() {
            super.onUnfocus();
            _pressed = false;
            // intentionally no invalidate() — no focus border to clear
        }
    }
}
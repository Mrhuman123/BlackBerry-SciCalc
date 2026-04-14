package net.mbeffects.calc;

import net.rim.device.api.util.MathUtilities;

// ============================================================
//  MathEngine.java  —  MBEffects Scientific Calculator
// ============================================================
//  BLACKBERRY CLDC 1.1 SAFE:
//    Math.sin / cos / tan   : OK (exist in CLDC java.lang.Math)
//    Math.sqrt / abs / floor: OK
//    Math.asin / acos / atan: NOT available — implemented manually
//    Math.toRadians/toDegrees: NOT available — use PI/180 constant
//    MathUtilities.log      : OK  (base-e log)
//    MathUtilities.pow      : OK
//    MathUtilities.atan2    : OK  (used for inverse trig)
// ============================================================
public class MathEngine {

    private static final double PI      = 3.14159265358979323846;
    private static final double DEG2RAD = PI / 180.0;
    private static final double RAD2DEG = 180.0 / PI;

    public static double eval(String expr) {
        if (expr == null || expr.trim().length() == 0)
            throw new IllegalArgumentException("Empty expression");
        return new Parser(expr).parse();
    }

    // ----------------------------------------------------------
    //  Inverse trig — CLDC has no Math.asin/acos/atan
    // ----------------------------------------------------------
    private static double asin(double x) {
        // asin(x) = atan2(x, sqrt(1 - x*x))
        return MathUtilities.atan2(x, Math.sqrt(1.0 - x * x));
    }
    private static double acos(double x) {
        // acos(x) = PI/2 - asin(x)
        return PI / 2.0 - asin(x);
    }
    private static double atan(double x) {
        // atan(x) = atan2(x, 1)
        return MathUtilities.atan2(x, 1.0);
    }

    // ----------------------------------------------------------
    //  Recursive descent parser
    // ----------------------------------------------------------
    private static final class Parser {
        private final String src;
        private int pos = -1, ch;

        Parser(String s) { src = s; }

        private void next() { ch = (++pos < src.length()) ? src.charAt(pos) : -1; }

        private boolean eat(int c) {
            while (ch == ' ') next();
            if (ch == c) { next(); return true; }
            return false;
        }

        double parse() {
            next();
            double v = expr();
            while (ch == ' ') next();
            if (ch != -1) throw new IllegalArgumentException(
                "Unexpected: '" + (char)ch + "'");
            return v;
        }

        private double expr() {
            double x = term();
            for (;;) {
                if      (eat('+')) x += term();
                else if (eat('-')) x -= term();
                else return x;
            }
        }

        private double term() {
            double x = factor();
            for (;;) {
                if      (eat('*')) x *= factor();
                else if (eat('/')) x /= factor();
                else return x;
            }
        }

        private double factor() {
            if (eat('+')) return  factor();
            if (eat('-')) return -factor();

            while (ch == ' ') next();
            double x;
            int start = pos;

            if (eat('(')) {
                x = expr();
                eat(')');

            } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                start = pos;
                while ((ch >= '0' && ch <= '9') || ch == '.') next();
                // scientific notation  e.g. 1.5E3 or 2e-4
                if (ch == 'E' || ch == 'e') {
                    next();
                    if (ch == '+' || ch == '-') next();
                    while (ch >= '0' && ch <= '9') next();
                }
                x = Double.parseDouble(src.substring(start, pos));

            } else if (ch >= 'a' && ch <= 'z') {
                start = pos;
                while (ch >= 'a' && ch <= 'z') next();
                x = applyFunc(src.substring(start, pos));

            } else {
                throw new IllegalArgumentException("Bad char: '" + (char)ch + "'");
            }

            // postfix ^
            if (eat('^')) x = MathUtilities.pow(x, factor());
            // postfix !
            if (eat('!')) x = factorial((int)x);

            return x;
        }

        private double applyFunc(String name) {
            double a = factor();   // reads the argument (parenthesised or bare)

            if (name.equals("sin"))  return Math.sin(a * DEG2RAD);
            if (name.equals("cos"))  return Math.cos(a * DEG2RAD);
            if (name.equals("tan"))  return Math.tan(a * DEG2RAD);
            if (name.equals("asin")) return asin(a) * RAD2DEG;
            if (name.equals("acos")) return acos(a) * RAD2DEG;
            if (name.equals("atan")) return atan(a) * RAD2DEG;
            if (name.equals("sqrt")) return Math.sqrt(a);
            if (name.equals("abs"))  return Math.abs(a);
            if (name.equals("log"))  return MathUtilities.log(a) / MathUtilities.log(10.0);
            if (name.equals("ln"))   return MathUtilities.log(a);

            throw new IllegalArgumentException("Unknown function: " + name);
        }

        private double factorial(int n) {
            if (n < 0) throw new IllegalArgumentException("Factorial of negative");
            if (n > 170) return Double.POSITIVE_INFINITY;
            double r = 1.0;
            for (int i = 2; i <= n; i++) r *= i;
            return r;
        }
    }
}

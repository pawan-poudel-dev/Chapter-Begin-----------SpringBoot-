package com.aidesktopagent.computer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Safe arithmetic evaluator — numbers and + - * / ( ) % only. No scripts.
 */
public final class SafeCalculator {

    public double evaluate(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Empty expression");
        }
        String s = expression.trim().replaceAll("\\s+", "");
        if (s.length() > 200) {
            throw new IllegalArgumentException("Expression too long");
        }
        if (!s.matches("[0-9.+\\-*/()%eE]+")) {
            throw new IllegalArgumentException("Only numbers and + - * / ( ) % allowed");
        }
        List<String> tokens = tokenize(s);
        List<String> rpn = toRpn(tokens);
        return evalRpn(rpn);
    }

    private static List<String> tokenize(String s) {
        List<String> t = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '+' || c == '-' || c == '*' || c == '/' || c == '%' || c == '(' || c == ')') {
                // unary minus
                if (c == '-' && (t.isEmpty() || isOp(t.get(t.size() - 1)) || t.get(t.size() - 1).equals("("))) {
                    int j = i + 1;
                    while (j < s.length() && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '.')) {
                        j++;
                    }
                    t.add(s.substring(i, j));
                    i = j;
                } else {
                    t.add(String.valueOf(c));
                    i++;
                }
            } else if (Character.isDigit(c) || c == '.') {
                int j = i + 1;
                while (j < s.length() && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '.'
                        || s.charAt(j) == 'e' || s.charAt(j) == 'E')) {
                    j++;
                }
                t.add(s.substring(i, j));
                i = j;
            } else {
                throw new IllegalArgumentException("Invalid character: " + c);
            }
        }
        return t;
    }

    private static boolean isOp(String x) {
        return x.equals("+") || x.equals("-") || x.equals("*") || x.equals("/") || x.equals("%");
    }

    private static int prec(String op) {
        return switch (op) {
            case "+", "-" -> 1;
            case "*", "/", "%" -> 2;
            default -> 0;
        };
    }

    private static List<String> toRpn(List<String> tokens) {
        List<String> out = new ArrayList<>();
        Deque<String> ops = new ArrayDeque<>();
        for (String tok : tokens) {
            if (tok.matches("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?")) {
                out.add(tok);
            } else if (isOp(tok)) {
                while (!ops.isEmpty() && isOp(ops.peek()) && prec(ops.peek()) >= prec(tok)) {
                    out.add(ops.pop());
                }
                ops.push(tok);
            } else if (tok.equals("(")) {
                ops.push(tok);
            } else if (tok.equals(")")) {
                while (!ops.isEmpty() && !ops.peek().equals("(")) {
                    out.add(ops.pop());
                }
                if (ops.isEmpty()) {
                    throw new IllegalArgumentException("Mismatched parentheses");
                }
                ops.pop();
            }
        }
        while (!ops.isEmpty()) {
            String o = ops.pop();
            if (o.equals("(") || o.equals(")")) {
                throw new IllegalArgumentException("Mismatched parentheses");
            }
            out.add(o);
        }
        return out;
    }

    private static double evalRpn(List<String> rpn) {
        Deque<Double> st = new ArrayDeque<>();
        for (String tok : rpn) {
            if (isOp(tok)) {
                if (st.size() < 2) {
                    throw new IllegalArgumentException("Malformed expression");
                }
                double b = st.pop();
                double a = st.pop();
                st.push(switch (tok) {
                    case "+" -> a + b;
                    case "-" -> a - b;
                    case "*" -> a * b;
                    case "/" -> {
                        if (b == 0) throw new IllegalArgumentException("Division by zero");
                        yield a / b;
                    }
                    case "%" -> a % b;
                    default -> throw new IllegalArgumentException("Unknown op");
                });
            } else {
                st.push(Double.parseDouble(tok));
            }
        }
        if (st.size() != 1) {
            throw new IllegalArgumentException("Malformed expression");
        }
        return st.pop();
    }

    public String format(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) {
            return String.format(Locale.ROOT, "%.0f", v);
        }
        return String.format(Locale.ROOT, "%.8g", v);
    }
}

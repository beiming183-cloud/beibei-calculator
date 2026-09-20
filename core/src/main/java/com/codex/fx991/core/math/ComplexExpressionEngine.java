package com.codex.fx991.core.math;

import com.codex.fx991.core.AngleUnit;

import java.util.Locale;
import java.util.Map;

/** Complex-mode expression evaluator supporting rectangular and polar input. */
public final class ComplexExpressionEngine {
    private ComplexExpressionEngine() {}

    public static ComplexValue evaluate(
            String expression, Map<String, ComplexValue> variables, ComplexValue ans, AngleUnit angleUnit) {
        Parser parser = new Parser(expression, variables == null ? com.codex.fx991.core.Compat.emptyMap() : variables,
                ans == null ? ComplexValue.ZERO : ans, angleUnit == null ? AngleUnit.DEG : angleUnit);
        ComplexValue value = parser.parseExpression();
        parser.skip();
        if (!parser.end()) throw new IllegalArgumentException("Syntax ERROR at " + parser.position);
        if (!Double.isFinite(value.real()) || !Double.isFinite(value.imaginary())
                || Math.abs(value.real()) > 9.999999999e99
                || Math.abs(value.imaginary()) > 9.999999999e99) {
            throw new ArithmeticException("Complex calculation range");
        }
        return value;
    }

    /**
     * Verifies one top-level relation. Equality uses a relative tolerance of
     * 1e-12 independently for each component, with no absolute floor at zero;
     * a small but nonzero imaginary component is never silently made real.
     */
    public static boolean verify(String expression, Map<String, ComplexValue> variables,
                                 ComplexValue ans, AngleUnit angleUnit) {
        String source = expression == null ? "" : expression;
        int depth = 0, relationIndex = -1;
        String relation = null;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '(') { depth++; continue; }
            if (current == ')') { depth--; continue; }
            if (depth != 0) continue;
            String found = null;
            if (index + 1 < source.length()) {
                String pair = source.substring(index, index + 2);
                if (pair.equals("<=") || pair.equals(">=") || pair.equals("!=") || pair.equals("==")) found = pair;
            }
            if (found == null && "=≠<>≤≥".indexOf(current) >= 0) found = Character.toString(current);
            if (found == null) continue;
            if (relation != null) throw new CalculationException(CalculationError.SYNTAX,
                    "Complex verification accepts one relation", index);
            relation = found;
            relationIndex = index;
            index += found.length() - 1;
        }
        if (relation == null) throw new CalculationException(CalculationError.NO_OPERATOR,
                "No relational operator", 0);
        ComplexValue left = evaluate(source.substring(0, relationIndex), variables, ans, angleUnit);
        ComplexValue right = evaluate(source.substring(relationIndex + relation.length()), variables, ans, angleUnit);
        boolean equal = equalComponent(left.real(), right.real())
                && equalComponent(left.imaginary(), right.imaginary());
        if (relation.equals("=") || relation.equals("==")) return equal;
        if (relation.equals("!=") || relation.equals("≠")) return !equal;
        if (left.imaginary() != 0.0 || right.imaginary() != 0.0) {
            throw new CalculationException(CalculationError.MATH,
                    "Complex values cannot be ordered", relationIndex);
        }
        return switch (relation) {
            case "<" -> left.real() < right.real();
            case ">" -> left.real() > right.real();
            case "<=", "≤" -> left.real() <= right.real();
            case ">=", "≥" -> left.real() >= right.real();
            default -> throw new AssertionError(relation);
        };
    }

    private static boolean equalComponent(double left, double right) {
        return left == right || Math.abs(left - right)
                <= 1e-12 * Math.max(Math.abs(left), Math.abs(right));
    }

    private static final class Parser {
        private final String source;
        private final Map<String, ComplexValue> variables;
        private final ComplexValue ans;
        private final AngleUnit angleUnit;
        private int position;

        Parser(String source, Map<String, ComplexValue> variables, ComplexValue ans, AngleUnit angleUnit) {
            this.source = source == null ? "" : source;
            this.variables = variables;
            this.ans = ans;
            this.angleUnit = angleUnit;
        }

        ComplexValue parseExpression() {
            ComplexValue value = parseTerm();
            while (true) {
                skip();
                if (take('+')) value = value.add(parseTerm());
                else if (take('-') || take('−')) value = value.subtract(parseTerm());
                else break;
            }
            return value;
        }

        ComplexValue parseTerm() {
            ComplexValue value = parseImplicitProduct();
            while (true) {
                skip();
                if (take('*') || take('×')) value = value.multiply(parseImplicitProduct());
                else if (take('/') || take('÷')) value = value.divide(parseImplicitProduct());
                else break;
            }
            return value;
        }

        // Same precedence as ScalarExpressionEngine: adjacent factors bind
        // before explicit multiplication/division. E.g. 1/2i = 1/(2*i).
        ComplexValue parseImplicitProduct() {
            ComplexValue value = parseUnary();
            while (startsPrimary()) value = value.multiply(parseUnary());
            return value;
        }

        ComplexValue parseUnary() {
            skip();
            if (take('+')) return parseUnary();
            if (take('-') || take('−') || take('~')) return parseUnary().negate();
            return parsePolar();
        }

        ComplexValue parsePolar() {
            ComplexValue value = parsePower();
            skip();
            if (take('∠')) {
                if (value.imaginary() != 0.0) throw new ArithmeticException("Polar radius must be real");
                ComplexValue angle = parseUnary();
                if (angle.imaginary() != 0.0) throw new ArithmeticException("Polar angle must be real");
                return new ComplexValue(value.real() * AngleTrig.cos(angle.real(), angleUnit),
                        value.real() * AngleTrig.sin(angle.real(), angleUnit));
            }
            return value;
        }

        ComplexValue parsePower() {
            ComplexValue value = parsePrimary();
            skip();
            if (take('^')) {
                ComplexValue exponent = parseUnary();
                if (exponent.imaginary() == 0.0 && exponent.real() == Math.rint(exponent.real())) {
                    if (Math.abs(exponent.real()) >= 10_000_000_000.0) {
                        throw new ArithmeticException("Exponent range");
                    }
                    return value.pow((long) exponent.real());
                }
                if (exponent.imaginary() == 0.0) return value.pow(exponent.real());
                return value.log().multiply(exponent).exp();
            }
            return value;
        }

        ComplexValue parsePrimary() {
            skip();
            if (take('(')) {
                ComplexValue value = parseExpression();
                require(')');
                return value;
            }
            if (end()) throw new IllegalArgumentException("Expected value");
            char c = peek();
            if (Character.isDigit(c) || c == '.') return new ComplexValue(number(), 0.0);
            if (Character.isLetter(c) || c == 'π') {
                String name = identifier();
                if (name.equals("i")) return ComplexValue.I;
                if (name.equalsIgnoreCase("pi") || name.equals("π")) return new ComplexValue(Math.PI, 0.0);
                if (name.equals("e")) return new ComplexValue(Math.E, 0.0);
                if (name.equalsIgnoreCase("Ans")) return ans;
                skip();
                if (!take('(')) {
                    ComplexValue value = variables.get(name);
                    if (value == null) value = variables.get(name.toUpperCase(Locale.ROOT));
                    if (value == null) throw new IllegalArgumentException("Undefined variable " + name);
                    return value;
                }
                ComplexValue argument = parseExpression();
                require(')');
                return function(name, argument);
            }
            throw new IllegalArgumentException("Unexpected '" + c + "'");
        }

        private ComplexValue function(String name, ComplexValue value) {
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "conj", "conjugate" -> value.conjugate();
                case "re", "real" -> new ComplexValue(value.real(), 0.0);
                case "im", "imag" -> new ComplexValue(value.imaginary(), 0.0);
                case "abs" -> new ComplexValue(value.abs(), 0.0);
                case "arg" -> new ComplexValue(fromRadians(value.argument(), angleUnit), 0.0);
                case "sqrt" -> value.sqrt();
                case "exp" -> value.exp();
                case "ln" -> value.log();
                case "log" -> value.log().multiply(1.0 / Math.log(10.0));
                case "sin" -> value.imaginary() == 0.0
                        ? new ComplexValue(AngleTrig.sin(value.real(), angleUnit), 0.0)
                        : complexSin(toRadians(value, angleUnit));
                case "cos" -> value.imaginary() == 0.0
                        ? new ComplexValue(AngleTrig.cos(value.real(), angleUnit), 0.0)
                        : complexCos(toRadians(value, angleUnit));
                case "tan" -> {
                    ComplexValue radians = toRadians(value, angleUnit);
                    yield complexSin(radians).divide(complexCos(radians));
                }
                default -> throw new IllegalArgumentException("Unknown function " + name);
            };
        }

        private double number() {
            int start = position;
            boolean exponent = false;
            while (!end()) {
                char c = peek();
                if (Character.isDigit(c) || c == '.') position++;
                else if ((c == 'E' || c == 'e') && !exponent) {
                    exponent = true; position++;
                    if (!end() && (peek() == '+' || peek() == '-')) position++;
                } else break;
            }
            return Double.parseDouble(source.substring(start, position));
        }

        private String identifier() {
            int start = position;
            while (!end() && (Character.isLetterOrDigit(peek()) || peek() == 'π')) position++;
            return source.substring(start, position);
        }

        private boolean startsPrimary() {
            skip();
            return !end() && (peek() == '(' || Character.isLetter(peek()) || Character.isDigit(peek()) || peek() == 'π');
        }
        private void require(char expected) { skip(); if (!take(expected)) throw new IllegalArgumentException("Expected " + expected); }
        private boolean take(char value) { if (!end() && source.charAt(position) == value) { position++; return true; } return false; }
        private char peek() { return source.charAt(position); }
        private boolean end() { return position >= source.length(); }
        private void skip() { while (!end() && Character.isWhitespace(peek())) position++; }
    }

    private static ComplexValue complexSin(ComplexValue z) {
        return new ComplexValue(AngleTrig.sinRadians(z.real()) * Math.cosh(z.imaginary()),
                AngleTrig.cosRadians(z.real()) * Math.sinh(z.imaginary()));
    }

    private static ComplexValue complexCos(ComplexValue z) {
        return new ComplexValue(AngleTrig.cosRadians(z.real()) * Math.cosh(z.imaginary()),
                -AngleTrig.sinRadians(z.real()) * Math.sinh(z.imaginary()));
    }

    private static ComplexValue toRadians(ComplexValue value, AngleUnit unit) {
        double scale = switch (unit) {
            case DEG -> Math.PI / 180.0;
            case RAD -> 1.0;
            case GRAD -> Math.PI / 200.0;
        };
        return value.multiply(scale);
    }

    private static double fromRadians(double value, AngleUnit unit) {
        return switch (unit) {
            case DEG -> Math.toDegrees(value);
            case RAD -> value;
            case GRAD -> value * 200.0 / Math.PI;
        };
    }
}

package com.codex.fx991.core.math;

import com.codex.fx991.core.AngleUnit;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Clean-room scalar expression compiler for CN CW modes. Parsing builds an AST, allowing
 * derivative/integral/sum and f(x)/g(x) to re-evaluate a subexpression at different x values.
 */
public final class ScalarExpressionEngine {
    private static final double MAX_MAGNITUDE = 9.999999999e99;
    private static final double MIN_MAGNITUDE = 1e-99;
    private ScalarExpressionEngine() {}

    public static CompiledExpression compile(String source) {
        Parser parser = new Parser(source == null ? "" : source);
        Node root = parser.parseExpression();
        parser.expect(TokenType.END);
        return new CompiledExpression(source, root);
    }

    public static double evaluate(String source, EvaluationContext context) {
        return compile(source).evaluate(context);
    }

    public static EvaluationResult evaluateDetailed(String source, EvaluationContext context) {
        return compile(source).evaluateDetailed(context);
    }

    public static boolean verify(String source, EvaluationContext context) {
        List<RelationPart> parts = splitRelations(source);
        if (parts.size() < 2) {
            throw new CalculationException(CalculationError.NO_OPERATOR,
                    "No relational operator", 0);
        }
        RelationDirection direction = null;
        boolean sawNotEqual = false;
        boolean sawOrderedRelation = false;
        for (int i = 0; i < parts.size() - 1; i++) {
            Relation relation = parts.get(i).relationAfter();
            RelationDirection current = relation.direction();
            if (relation == Relation.NE) sawNotEqual = true;
            if (current != RelationDirection.NEUTRAL) sawOrderedRelation = true;
            if (sawNotEqual && sawOrderedRelation) {
                throw new SyntaxException("Cannot mix != with an inequality", 0);
            }
            if (direction != null && current != RelationDirection.NEUTRAL
                    && direction != RelationDirection.NEUTRAL && current != direction) {
                throw new SyntaxException("Mixed relation direction", 0);
            }
            if (current != RelationDirection.NEUTRAL) direction = current;
            double left = evaluate(parts.get(i).expression(), context);
            double right = evaluate(parts.get(i + 1).expression(), context);
            if (!relation.test(left, right)) return false;
        }
        return true;
    }

    public static final class CompiledExpression {
        private final String source;
        private final Node root;
        private CompiledExpression(String source, Node root) { this.source = source; this.root = root; }
        public String source() { return source; }
        public double evaluate(EvaluationContext context) {
            return evaluateDetailed(context).value();
        }
        public EvaluationResult evaluateDetailed(EvaluationContext context) {
            EvaluationContext safe = context == null ? EvaluationContext.standard() : context;
            try {
                double value = bounded(root.evaluate(safe, 0), root.position());
                ExactValue exact = root.exact(safe, 0);
                if (exact != null && Math.abs(exact.toDouble() - value)
                        > 1e-11 * Math.max(1.0, Math.abs(value))) exact = null;
                return new EvaluationResult(value, exact);
            } catch (CalculationException error) {
                throw error;
            } catch (ArithmeticException error) {
                throw new CalculationException(CalculationError.MATH,
                        error.getMessage(), root.position(), error);
            } catch (IllegalArgumentException error) {
                throw new CalculationException(CalculationError.ARGUMENT,
                        error.getMessage(), root.position(), error);
            }
        }
    }

    public record EvaluationResult(double value, ExactValue exactValue) {
        public boolean hasExactValue() { return exactValue != null; }
    }

    public enum RoundingKind { NORM, FIX, SCI }

    public record RoundingPolicy(RoundingKind kind, int digits) {
        public RoundingPolicy {
            if (kind == null) kind = RoundingKind.NORM;
            if (kind == RoundingKind.FIX && (digits < 0 || digits > 9)) {
                throw new IllegalArgumentException("FIX digits must be 0..9");
            }
            if (kind == RoundingKind.SCI && (digits < 1 || digits > 10)) {
                throw new IllegalArgumentException("SCI digits must be 1..10");
            }
        }

        public static RoundingPolicy norm() { return new RoundingPolicy(RoundingKind.NORM, 10); }
    }

    public static final class EvaluationContext {
        private final Map<String, Double> variables;
        private final AngleUnit angleUnit;
        private final double ans;
        private final CompiledExpression f;
        private final CompiledExpression g;
        private final Double xOverride;
        private final ExactValue exactXOverride;
        private final Map<String, ExactValue> exactVariables;
        private final ExactValue exactAns;
        private final Random random;
        private final RoundingPolicy roundingPolicy;
        private final Set<String> functionStack;

        public EvaluationContext(
                Map<String, Double> variables,
                AngleUnit angleUnit,
                double ans,
                CompiledExpression f,
                CompiledExpression g,
                Random random) {
            this(variables, angleUnit, ans, f, g, null, null,
                    com.codex.fx991.core.Compat.emptyMap(), null, random,
                    RoundingPolicy.norm(), Collections.emptySet());
        }

        public EvaluationContext(
                Map<String, Double> variables,
                Map<String, ExactValue> exactVariables,
                AngleUnit angleUnit,
                double ans,
                ExactValue exactAns,
                CompiledExpression f,
                CompiledExpression g,
                Random random,
                RoundingPolicy roundingPolicy) {
            this(variables, angleUnit, ans, f, g, null, null,
                    exactVariables, exactAns, random, roundingPolicy,
                    Collections.emptySet());
        }

        private EvaluationContext(
                Map<String, Double> variables,
                AngleUnit angleUnit,
                double ans,
                CompiledExpression f,
                CompiledExpression g,
                Double xOverride,
                ExactValue exactXOverride,
                Map<String, ExactValue> exactVariables,
                ExactValue exactAns,
                Random random,
                RoundingPolicy roundingPolicy,
                Set<String> functionStack) {
            this.variables = com.codex.fx991.core.Compat.copyMap(variables == null ? com.codex.fx991.core.Compat.emptyMap() : variables);
            this.angleUnit = angleUnit == null ? AngleUnit.DEG : angleUnit;
            this.ans = ans;
            this.f = f;
            this.g = g;
            this.xOverride = xOverride;
            this.exactXOverride = exactXOverride;
            this.exactVariables = com.codex.fx991.core.Compat.copyMap(
                    exactVariables == null ? com.codex.fx991.core.Compat.emptyMap() : exactVariables);
            this.exactAns = exactAns;
            this.random = random == null ? new Random() : random;
            this.roundingPolicy = roundingPolicy == null ? RoundingPolicy.norm() : roundingPolicy;
            this.functionStack = Collections.unmodifiableSet(new HashSet<>(functionStack == null
                    ? Collections.emptySet() : functionStack));
        }

        public static EvaluationContext standard() {
            Map<String, Double> variables = new HashMap<>();
            for (String name : com.codex.fx991.core.Compat.list("A", "B", "C", "D", "E", "F", "x", "y", "z")) {
                variables.put(name, 0.0);
            }
            return new EvaluationContext(variables, AngleUnit.DEG, 0.0, null, null,
                    new Random());
        }

        public EvaluationContext withX(double x) {
            return withX(x, null);
        }

        public EvaluationContext withX(double x, ExactValue exactX) {
            return new EvaluationContext(variables, angleUnit, ans, f, g, x, exactX,
                    exactVariables, exactAns, random, roundingPolicy, functionStack);
        }

        EvaluationContext enterFunction(String name, double x, ExactValue exactX, int position) {
            if (functionStack.contains(name)) {
                throw new CalculationException(CalculationError.CIRCULAR,
                        "Circular function reference", position);
            }
            Set<String> stack = new HashSet<>(functionStack);
            stack.add(name);
            return new EvaluationContext(variables, angleUnit, ans, f, g, x, exactX,
                    exactVariables, exactAns, random, roundingPolicy, stack);
        }

        public double variable(String name) {
            if (name.equals("Ans")) return ans;
            if (name.equals("x") && xOverride != null) return xOverride;
            Double value = variables.get(name);
            if (value == null) throw new CalculationException(CalculationError.UNDEFINED,
                    "Undefined variable " + name, 0);
            return value;
        }

        ExactValue exactVariable(String name) {
            if (name.equals("Ans")) return exactAns;
            if (name.equals("x") && xOverride != null) return exactXOverride;
            ExactValue exact = exactVariables.get(name);
            if (exact != null) return exact;
            Double value = variables.get(name);
            if (value != null && Double.isFinite(value) && value == Math.rint(value)
                    && Math.abs(value) <= Long.MAX_VALUE) return ExactValue.integer((long) value.doubleValue());
            return null;
        }

        AngleUnit angleUnit() { return angleUnit; }
        Random random() { return random; }
        CompiledExpression function(String name) { return name.equalsIgnoreCase("f") ? f : g; }
        double round(double value) { return roundToDisplayPrecision(value, roundingPolicy); }
    }

    private interface Node {
        double evaluate(EvaluationContext context, int depth);
        int position();
        default ExactValue exact(EvaluationContext context, int depth) { return null; }
    }

    private record NumberNode(String literal, double value, int position) implements Node {
        @Override public double evaluate(EvaluationContext context, int depth) {
            checkDepth(depth, position);
            return bounded(value, position);
        }
        @Override public ExactValue exact(EvaluationContext context, int depth) {
            return ExactValue.decimal(literal);
        }
    }

    private record ConstantNode(ExactValue value, int position) implements Node {
        @Override public double evaluate(EvaluationContext context, int depth) {
            checkDepth(depth, position);
            return value.toDouble();
        }
        @Override public ExactValue exact(EvaluationContext context, int depth) { return value; }
    }

    private record VariableNode(String name, int position) implements Node {
        @Override public double evaluate(EvaluationContext context, int depth) {
            checkDepth(depth, position);
            return bounded(context.variable(name), position);
        }
        @Override public ExactValue exact(EvaluationContext context, int depth) {
            return context.exactVariable(name);
        }
    }

    private record UnaryNode(char operator, Node operand, int position) implements Node {
        @Override public double evaluate(EvaluationContext context, int depth) {
            checkDepth(depth, position);
            double value = operand.evaluate(context, depth + 1);
            return bounded(operator == '-' ? -value : value, position);
        }
        @Override public ExactValue exact(EvaluationContext context, int depth) {
            ExactValue value = operand.exact(context, depth + 1);
            return value == null || operator != '-' ? value : value.negate();
        }
    }

    private record BinaryNode(String operator, Node left, Node right, int position) implements Node {
        @Override public double evaluate(EvaluationContext context, int depth) {
            checkDepth(depth, position);
            double a = left.evaluate(context, depth + 1);
            double b = right.evaluate(context, depth + 1);
            double result = switch (operator) {
                case "+" -> a + b;
                case "-" -> a - b;
                case "*" -> a * b;
                case "/" -> {
                    if (b == 0.0) throw math("Division by zero", right.position());
                    yield a / b;
                }
                case "÷R" -> ManualFunctions.divideWithRemainder(a, b).ansValue();
                case "^" -> power(a, b, right.exact(context, depth + 1), position);
                case "nPr" -> permutation(a, b, position);
                case "nCr" -> combination(a, b, position);
                default -> throw new AssertionError(operator);
            };
            return bounded(result, position);
        }

        @Override public ExactValue exact(EvaluationContext context, int depth) {
            ExactValue a = left.exact(context, depth + 1);
            ExactValue b = right.exact(context, depth + 1);
            if (a == null || b == null) return null;
            return switch (operator) {
                case "+" -> a.add(b);
                case "-" -> a.subtract(b);
                case "*" -> a.multiply(b);
                case "/" -> b.isZero() ? null : a.divide(b);
                case "^" -> {
                    Rational exponent = b.rational();
                    if (exponent == null || !exponent.isInteger()
                            || exponent.numerator().bitLength() > 31) yield null;
                    yield a.pow(exponent.numerator().intValue());
                }
                case "nPr", "nCr", "÷R" -> exactInteger(evaluate(context, depth));
                default -> null;
            };
        }
    }

    private record PostfixNode(String operator, Node operand, int position) implements Node {
        @Override public double evaluate(EvaluationContext context, int depth) {
            checkDepth(depth, position);
            double value = operand.evaluate(context, depth + 1);
            return bounded(switch (operator) {
                case "!" -> factorial(value, position);
                case "%" -> value / 100.0;
                default -> throw new AssertionError(operator);
            }, position);
        }
        @Override public ExactValue exact(EvaluationContext context, int depth) {
            ExactValue value = operand.exact(context, depth + 1);
            if (value == null) return null;
            if (operator.equals("%")) return value.divide(ExactValue.integer(100));
            if (operator.equals("!")) return exactInteger(evaluate(context, depth));
            return null;
        }
    }

    private record FunctionNode(String name, List<Node> arguments, int position) implements Node {
        @Override public double evaluate(EvaluationContext context, int depth) {
            checkDepth(depth, position);
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.equals("f") || lower.equals("g")) {
                requireCount(1);
                CompiledExpression definition = context.function(lower);
                if (definition == null) {
                    throw new CalculationException(CalculationError.UNDEFINED,
                            "Undefined " + lower + "(x)", position);
                }
                double argument = value(0, context, depth);
                ExactValue exactArgument = exactValue(0, context, depth);
                return definition.root.evaluate(
                        context.enterFunction(lower, argument, exactArgument, position), depth + 1);
            }
            if (lower.equals("diff") || lower.equals("derivative")) {
                requireRange(2, 3);
                double point = value(1, context, depth);
                double tolerance = arguments.size() == 3 ? value(2, context, depth) : 1e-16;
                return bounded(NumericAnalysis.derivative(
                        x -> arguments.get(0).evaluate(context.withX(x), depth + 1),
                        point, tolerance), position);
            }
            if (lower.equals("integral") || lower.equals("int")) {
                requireRange(3, 4);
                double lowerBound = value(1, context, depth);
                double upperBound = value(2, context, depth);
                double tolerance = arguments.size() == 4 ? value(3, context, depth) : 1e-10;
                return bounded(NumericAnalysis.integrate(
                        x -> arguments.get(0).evaluate(context.withX(x), depth + 1),
                        lowerBound, upperBound, tolerance), position);
            }
            if (lower.equals("sum")) {
                requireCount(3);
                long start = integer(value(1, context, depth), arguments.get(1).position());
                long end = integer(value(2, context, depth), arguments.get(2).position());
                return bounded(NumericAnalysis.sum(
                        x -> arguments.get(0).evaluate(context.withX(x), depth + 1), start, end),
                        position);
            }
            if (lower.equals("ran") || lower.equals("ran#")) {
                requireCount(0);
                return Math.floor(context.random().nextDouble() * 1000.0) / 1000.0;
            }
            if (lower.equals("ranint") || lower.equals("ranint#")) {
                requireCount(2);
                long start = integer(value(0, context, depth), arguments.get(0).position());
                long end = integer(value(1, context, depth), arguments.get(1).position());
                if (start >= end || start <= -10_000_000_000L || start >= 10_000_000_000L
                        || end <= -10_000_000_000L || end >= 10_000_000_000L
                        || end - start >= 10_000_000_000L) {
                    throw argument("RanInt range", position);
                }
                long span = end - start + 1L;
                return start + (long) Math.floor(context.random().nextDouble() * span);
            }
            if (lower.equals("root")) {
                requireCount(2);
                double degree = value(0, context, depth);
                double radicand = value(1, context, depth);
                return bounded(nthRoot(radicand, degree, position), position);
            }
            if (lower.equals("mixed")) {
                requireCount(3);
                double whole = value(0, context, depth);
                double numerator = value(1, context, depth);
                double denominator = value(2, context, depth);
                if (!Double.isFinite(whole) || whole != Math.rint(whole)
                        || !Double.isFinite(numerator) || numerator != Math.rint(numerator)
                        || !Double.isFinite(denominator) || denominator != Math.rint(denominator)
                        || denominator <= 0.0) {
                    throw argument("mixed requires integer terms", position);
                }
                double fraction = numerator / denominator;
                return bounded(whole < 0.0 ? whole - fraction : whole + fraction, position);
            }
            if (lower.equals("dms")) {
                requireCount(3);
                double degrees = value(0, context, depth);
                double minutes = value(1, context, depth);
                double seconds = value(2, context, depth);
                if (degrees != Math.rint(degrees) || minutes != Math.rint(minutes)) {
                    throw argument("DMS degree/minute must be integers", position);
                }
                int sign = Double.doubleToRawLongBits(degrees) < 0 ? -1 : 1;
                return ManualFunctions.toDecimalDegrees(sign, (long) Math.abs(degrees),
                        (int) minutes, seconds);
            }
            if (lower.equals("log")) {
                requireRange(1, 2);
                if (arguments.size() == 1) {
                    double x = value(0, context, depth);
                    if (!(x > 0.0)) throw math("Log domain", arguments.get(0).position());
                    return bounded(Math.log10(x), position);
                }
                double base = value(0, context, depth);
                double x = value(1, context, depth);
                if (!(base > 0.0) || base == 1.0 || !(x > 0.0)) {
                    throw math("Log domain", position);
                }
                return bounded(Math.log(x) / Math.log(base), position);
            }
            if (lower.equals("simp")) {
                requireRange(1, 2);
                return value(0, context, depth);
            }
            if (lower.startsWith("conv")) {
                requireCount(1);
                int conversion;
                try { conversion = Integer.parseInt(lower.substring(4)); }
                catch (NumberFormatException error) { throw argument("Conversion id", position); }
                List<String> commands = new ArrayList<>(UnitConverter.catalog().keySet());
                if (conversion < 0 || conversion >= commands.size()) {
                    throw argument("Conversion id", position);
                }
                return bounded(UnitConverter.convert(commands.get(conversion),
                        value(0, context, depth)), position);
            }
            if (lower.equals("deg") || lower.equals("rad") || lower.equals("grad")) {
                requireCount(1);
                double source = value(0, context, depth);
                double radians = lower.equals("deg") ? Math.toRadians(source)
                        : lower.equals("grad") ? source * Math.PI / 200.0 : source;
                return bounded(fromRadians(radians, context.angleUnit()), position);
            }
            if (lower.equals("pol") || lower.equals("rec")) {
                requireCount(2);
                ManualFunctions.CoordinatePair pair = lower.equals("pol")
                        ? ManualFunctions.polar(value(0, context, depth), value(1, context, depth),
                                context.angleUnit())
                        : ManualFunctions.rectangular(value(0, context, depth), value(1, context, depth),
                                context.angleUnit());
                return bounded(pair.first(), position);
            }
            requireCount(1);
            double x = value(0, context, depth);
            return bounded(evaluateUnary(lower, x, context, arguments.get(0).position()), position);
        }

        @Override public ExactValue exact(EvaluationContext context, int depth) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.equals("f") || lower.equals("g")) {
                if (arguments.size() != 1) return null;
                CompiledExpression definition = context.function(lower);
                ExactValue argument = exactValue(0, context, depth);
                if (definition == null || argument == null) return null;
                EvaluationContext child = context.enterFunction(lower, argument.toDouble(),
                        argument, position);
                return definition.root.exact(child, depth + 1);
            }
            if (lower.equals("root") && arguments.size() == 2) {
                ExactValue degreeValue = exactValue(0, context, depth);
                ExactValue radicand = exactValue(1, context, depth);
                Rational degree = degreeValue == null ? null : degreeValue.rational();
                Rational value = radicand == null ? null : radicand.rational();
                if (degree == null || value == null || !degree.isInteger()
                        || degree.numerator().bitLength() > 31) return null;
                return ExactValue.root(value, degree.numerator().intValue());
            }
            if (lower.equals("simp") && !arguments.isEmpty()) {
                return exactValue(0, context, depth);
            }
            if (lower.equals("mixed") && arguments.size() == 3) {
                ExactValue wholeValue = exactValue(0, context, depth);
                ExactValue numeratorValue = exactValue(1, context, depth);
                ExactValue denominatorValue = exactValue(2, context, depth);
                Rational whole = wholeValue == null ? null : wholeValue.rational();
                Rational numerator = numeratorValue == null ? null : numeratorValue.rational();
                Rational denominator = denominatorValue == null ? null : denominatorValue.rational();
                if (whole == null || numerator == null || denominator == null
                        || !whole.isInteger() || !numerator.isInteger()
                        || !denominator.isInteger() || denominator.numerator().signum() <= 0) {
                    return null;
                }
                Rational fraction = numerator.divide(denominator);
                return ExactValue.rational(whole.numerator().signum() < 0
                        ? whole.subtract(fraction) : whole.add(fraction));
            }
            if (arguments.size() != 1) return null;
            ExactValue argument = exactValue(0, context, depth);
            if (argument == null) return null;
            Rational rational = argument.rational();
            return switch (lower) {
                case "sqrt" -> rational == null ? null : ExactValue.sqrt(rational);
                case "cbrt" -> rational == null ? null : ExactValue.root(rational, 3);
                case "abs" -> argument.toDouble() < 0.0 ? argument.negate() : argument;
                case "sin", "cos", "tan" -> exactTrig(lower, argument, context.angleUnit());
                case "asin", "acos", "atan" -> exactInverseTrig(lower, argument,
                        context.angleUnit());
                case "floor" -> ExactValue.integer((long) Math.floor(argument.toDouble()));
                case "ceil" -> ExactValue.integer((long) Math.ceil(argument.toDouble()));
                case "sign" -> ExactValue.integer((long) Math.signum(argument.toDouble()));
                default -> null;
            };
        }

        private double value(int index, EvaluationContext context, int depth) {
            return arguments.get(index).evaluate(context, depth + 1);
        }
        private ExactValue exactValue(int index, EvaluationContext context, int depth) {
            return arguments.get(index).exact(context, depth + 1);
        }
        private void requireCount(int count) {
            if (arguments.size() != count) throw argument(name + " argument count", position);
        }
        private void requireRange(int minimum, int maximum) {
            if (arguments.size() < minimum || arguments.size() > maximum) {
                throw argument(name + " argument count", position);
            }
        }
    }

    private enum TokenType { NUMBER, IDENTIFIER, PLUS, MINUS, MULTIPLY, DIVIDE,
        REMAINDER_DIVIDE, POWER,
        FACTORIAL, PERCENT, OPEN, CLOSE, COMMA, NPR, NCR, END }
    private record Token(TokenType type, String text, int position) {}

    private static final class Parser {
        private final Lexer lexer;
        private Token current;
        private Parser(String source) { lexer = new Lexer(source); current = lexer.next(); }

        private Node parseExpression() {
            Node node = parseExplicitProduct();
            while (current.type == TokenType.PLUS || current.type == TokenType.MINUS) {
                String operator = current.type == TokenType.PLUS ? "+" : "-";
                int position = current.position;
                advance();
                node = new BinaryNode(operator, node, parseExplicitProduct(), position);
            }
            return node;
        }

        private Node parseExplicitProduct() {
            Node node = parsePermutation();
            while (true) {
                if (current.type == TokenType.MULTIPLY || current.type == TokenType.DIVIDE
                        || current.type == TokenType.REMAINDER_DIVIDE) {
                    String operator = current.type == TokenType.MULTIPLY ? "*"
                            : current.type == TokenType.DIVIDE ? "/" : "÷R";
                    int position = current.position;
                    advance();
                    node = new BinaryNode(operator, node, parsePermutation(), position);
                } else break;
            }
            return node;
        }

        private Node parsePermutation() {
            Node node = parseImplicitProduct();
            while (current.type == TokenType.NPR || current.type == TokenType.NCR) {
                String operator = current.type == TokenType.NPR ? "nPr" : "nCr";
                int position = current.position;
                advance();
                node = new BinaryNode(operator, node, parseImplicitProduct(), position);
            }
            return node;
        }

        private Node parseImplicitProduct() {
            Node node = parseUnary();
            while (startsPrimary(current.type)) {
                int position = current.position;
                node = new BinaryNode("*", node, parseUnary(), position);
            }
            return node;
        }

        private Node parseUnary() {
            if (current.type == TokenType.PLUS || current.type == TokenType.MINUS) {
                char operator = current.type == TokenType.MINUS ? '-' : '+';
                int position = current.position;
                advance();
                return new UnaryNode(operator, parseUnary(), position);
            }
            return parsePower();
        }

        private Node parsePower() {
            Node node = parsePostfix();
            if (current.type == TokenType.POWER) {
                int position = current.position;
                advance();
                node = new BinaryNode("^", node, parseUnary(), position);
            }
            return node;
        }

        private Node parsePostfix() {
            Node node = parsePrimary();
            while (current.type == TokenType.FACTORIAL || current.type == TokenType.PERCENT) {
                String operator = current.type == TokenType.FACTORIAL ? "!" : "%";
                int position = current.position;
                advance();
                node = new PostfixNode(operator, node, position);
            }
            return node;
        }

        private Node parsePrimary() {
            if (current.type == TokenType.NUMBER) {
                Token token = current; advance();
                try { return new NumberNode(token.text, Double.parseDouble(token.text), token.position); }
                catch (NumberFormatException error) { throw new SyntaxException("Invalid number", token.position); }
            }
            if (current.type == TokenType.IDENTIFIER) {
                Token token = current;
                String name = current.text; advance();
                if (name.equalsIgnoreCase("pi") || name.equals("π")) {
                    return new ConstantNode(ExactValue.PI, token.position);
                }
                if (name.equals("e")) return new ConstantNode(ExactValue.E, token.position);
                if (current.type != TokenType.OPEN) {
                    return new VariableNode(canonicalVariable(name), token.position);
                }
                advance();
                List<Node> arguments = new ArrayList<>();
                if (current.type != TokenType.CLOSE) {
                    do {
                        arguments.add(parseExpression());
                        if (current.type != TokenType.COMMA) break;
                        advance();
                    } while (true);
                }
                expect(TokenType.CLOSE);
                return new FunctionNode(name, com.codex.fx991.core.Compat.copyList(arguments),
                        token.position);
            }
            if (current.type == TokenType.OPEN) {
                advance();
                Node node = parseExpression();
                expect(TokenType.CLOSE);
                return node;
            }
            throw new SyntaxException("Expected value", current.position);
        }

        private void expect(TokenType type) {
            if (current.type != type) throw new SyntaxException("Expected " + type, current.position);
            advance();
        }
        private void advance() { current = lexer.next(); }
        private static boolean startsPrimary(TokenType type) {
            return type == TokenType.NUMBER || type == TokenType.IDENTIFIER || type == TokenType.OPEN;
        }
    }

    private static final class Lexer {
        private final String source;
        private int position;
        private Lexer(String source) { this.source = source; }

        private Token next() {
            while (position < source.length() && (Character.isWhitespace(source.charAt(position))
                    || source.charAt(position) == '\u2063')) position++;
            if (position >= source.length()) return new Token(TokenType.END, "", position);
            int start = position;
            char c = source.charAt(position++);
            return switch (c) {
                case '+' -> new Token(TokenType.PLUS, "+", start);
                case '-', '−', '~' -> new Token(TokenType.MINUS, "-", start);
                case '*', '×', '·' -> new Token(TokenType.MULTIPLY, "*", start);
                case '/' -> new Token(TokenType.DIVIDE, "/", start);
                case '÷' -> {
                    if (position < source.length()
                            && (source.charAt(position) == 'R' || source.charAt(position) == 'r')) {
                        position++;
                        yield new Token(TokenType.REMAINDER_DIVIDE, "÷R", start);
                    }
                    yield new Token(TokenType.DIVIDE, "/", start);
                }
                case '^' -> new Token(TokenType.POWER, "^", start);
                case '!' -> new Token(TokenType.FACTORIAL, "!", start);
                case '%' -> new Token(TokenType.PERCENT, "%", start);
                case '(' -> new Token(TokenType.OPEN, "(", start);
                case ')' -> new Token(TokenType.CLOSE, ")", start);
                case ',' -> new Token(TokenType.COMMA, ",", start);
                default -> {
                    if (Character.isDigit(c) || c == '.') yield number(start);
                    if (Character.isLetter(c) || c == 'π' || c == 'ℏ' || c == '_') yield identifier(start);
                    throw new SyntaxException("Unexpected '" + c + "'", start);
                }
            };
        }

        private Token number(int start) {
            boolean exponent = false;
            while (position < source.length()) {
                char c = source.charAt(position);
                if (Character.isDigit(c) || c == '.') position++;
                else if ((c == 'E' || c == 'e') && !exponent) {
                    exponent = true; position++;
                    if (position < source.length() && (source.charAt(position) == '+' || source.charAt(position) == '-')) position++;
                } else break;
            }
            return new Token(TokenType.NUMBER, source.substring(start, position), start);
        }

        private Token identifier(int start) {
            if (regionMatches(start, "nPr") || regionMatches(start, "nCr")) {
                position = start + 3;
                String operator = source.substring(start, position);
                return new Token(operator.equalsIgnoreCase("nPr") ? TokenType.NPR : TokenType.NCR,
                        operator, start);
            }
            while (position < source.length()) {
                char c = source.charAt(position);
                if (Character.isLetterOrDigit(c) || c == '#' || c == '_' || c == 'π') position++;
                else break;
            }
            String text = source.substring(start, position);
            if (text.equalsIgnoreCase("nPr")) return new Token(TokenType.NPR, text, start);
            if (text.equalsIgnoreCase("nCr")) return new Token(TokenType.NCR, text, start);
            return new Token(TokenType.IDENTIFIER, text, start);
        }

        private boolean regionMatches(int start, String value) {
            return start + value.length() <= source.length()
                    && source.regionMatches(true, start, value, 0, value.length());
        }
    }

    private enum RelationDirection { ASCENDING, DESCENDING, NEUTRAL }
    private enum Relation {
        EQ(RelationDirection.NEUTRAL), NE(RelationDirection.NEUTRAL),
        LT(RelationDirection.ASCENDING), LE(RelationDirection.ASCENDING),
        GT(RelationDirection.DESCENDING), GE(RelationDirection.DESCENDING);
        private final RelationDirection direction;
        Relation(RelationDirection direction) { this.direction = direction; }
        RelationDirection direction() { return direction; }
        boolean test(double left, double right) {
            double tolerance = 1e-10 * Math.max(1.0, Math.max(Math.abs(left), Math.abs(right)));
            return switch (this) {
                case EQ -> Math.abs(left - right) <= tolerance;
                case NE -> Math.abs(left - right) > tolerance;
                case LT -> left < right - tolerance;
                case LE -> left <= right + tolerance;
                case GT -> left > right + tolerance;
                case GE -> left >= right - tolerance;
            };
        }
    }
    private record RelationPart(String expression, Relation relationAfter) {}

    private static List<RelationPart> splitRelations(String source) {
        List<RelationPart> result = new ArrayList<>();
        int depth = 0, start = 0, index = 0;
        while (index < source.length()) {
            char c = source.charAt(index);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            if (depth == 0) {
                Match relation = relationAt(source, index);
                if (relation != null) {
                    result.add(new RelationPart(source.substring(start, index), relation.relation()));
                    index += relation.length(); start = index; continue;
                }
            }
            index++;
        }
        result.add(new RelationPart(source.substring(start), null));
        return result;
    }

    private static Match relationAt(String source, int index) {
        String remaining = source.substring(index);
        if (remaining.startsWith("<=" ) || remaining.startsWith("≤")) return new Match(Relation.LE, remaining.startsWith("≤") ? 1 : 2);
        if (remaining.startsWith(">=" ) || remaining.startsWith("≥")) return new Match(Relation.GE, remaining.startsWith("≥") ? 1 : 2);
        if (remaining.startsWith("!=" ) || remaining.startsWith("≠")) return new Match(Relation.NE, remaining.startsWith("≠") ? 1 : 2);
        if (remaining.startsWith("==")) return new Match(Relation.EQ, 2);
        if (remaining.startsWith("=")) return new Match(Relation.EQ, 1);
        if (remaining.startsWith("<")) return new Match(Relation.LT, 1);
        if (remaining.startsWith(">")) return new Match(Relation.GT, 1);
        return null;
    }
    private record Match(Relation relation, int length) {}

    private static String canonicalVariable(String name) {
        if (name.equalsIgnoreCase("ans")) return "Ans";
        if (name.length() == 1) {
            char c = name.charAt(0);
            if (c >= 'a' && c <= 'f') return name.toUpperCase(Locale.ROOT);
            if (c == 'X' || c == 'Y' || c == 'Z') return name.toLowerCase(Locale.ROOT);
        }
        return name;
    }

    private static double bounded(double value, int position) {
        if (!Double.isFinite(value) || Math.abs(value) > MAX_MAGNITUDE) {
            throw math("Calculation range", position);
        }
        if (value != 0.0 && Math.abs(value) < MIN_MAGNITUDE) return 0.0;
        return value == -0.0 ? 0.0 : value;
    }

    private static void checkDepth(int depth, int position) {
        if (depth > 64) {
            throw new CalculationException(CalculationError.STACK,
                    "Expression stack depth", position);
        }
    }

    private static CalculationException math(String detail, int position) {
        return new CalculationException(CalculationError.MATH, detail, position);
    }

    private static CalculationException argument(String detail, int position) {
        return new CalculationException(CalculationError.ARGUMENT, detail, position);
    }

    private static ExactValue exactInteger(double value) {
        if (!Double.isFinite(value) || value != Math.rint(value)
                || Math.abs(value) > Long.MAX_VALUE) return null;
        return ExactValue.integer((long) value);
    }

    private static double factorial(double value, int position) {
        long integer = integer(value, position);
        if (integer < 0 || integer > 69) throw math("Factorial range", position);
        double result = 1.0;
        for (long i = 2; i <= integer; i++) result *= i;
        return bounded(result, position);
    }

    private static double permutation(double nValue, double rValue, int position) {
        long n = integer(nValue, position), r = integer(rValue, position);
        if (n < 0 || r < 0 || r > n || n >= 10_000_000_000L) {
            throw math("nPr range", position);
        }
        double result = 1.0;
        for (long i = 0; i < r; i++) {
            result *= n - i;
            if (result > MAX_MAGNITUDE) throw math("nPr overflow", position);
        }
        return result;
    }

    private static double combination(double nValue, double rValue, int position) {
        long n = integer(nValue, position), r = integer(rValue, position);
        if (n < 0 || r < 0 || r > n || n >= 10_000_000_000L) {
            throw math("nCr range", position);
        }
        r = Math.min(r, n - r);
        double result = 1.0;
        for (long i = 1; i <= r; i++) {
            result = result * (n - r + i) / i;
            if (result > MAX_MAGNITUDE) throw math("nCr overflow", position);
        }
        return result;
    }

    private static long integer(double value, int position) {
        if (!Double.isFinite(value) || value != Math.rint(value)) {
            throw argument("Expected integer", position);
        }
        if (value < Long.MIN_VALUE || value > Long.MAX_VALUE) {
            throw argument("Integer range", position);
        }
        return (long) value;
    }

    private static double power(double base, double exponent, ExactValue exactExponent,
                                int position) {
        if (base == 0.0 && exponent <= 0.0) throw math("Power domain", position);
        if (base < 0.0 && exponent != Math.rint(exponent)) {
            Rational rational = exactExponent == null ? null : exactExponent.rational();
            if (rational == null || rational.denominator().mod(java.math.BigInteger.TWO).signum() == 0) {
                throw math("Power domain", position);
            }
            double magnitude = Math.pow(-base, exponent);
            return rational.numerator().abs().testBit(0) ? -magnitude : magnitude;
        }
        return Math.pow(base, exponent);
    }

    private static double nthRoot(double radicand, double degree, int position) {
        if (!Double.isFinite(degree) || degree == 0.0) throw math("Root domain", position);
        if (radicand < 0.0) {
            if (degree != Math.rint(degree) || (((long) degree) & 1L) == 0L) {
                throw math("Root domain", position);
            }
            return -Math.pow(-radicand, 1.0 / degree);
        }
        return Math.pow(radicand, 1.0 / degree);
    }

    private static double evaluateUnary(String function, double x, EvaluationContext context,
                                        int position) {
        return switch (function) {
            case "sin" -> {
                validateTrigArgument(x, context.angleUnit(), position);
                yield AngleTrig.sin(x, context.angleUnit());
            }
            case "cos" -> {
                validateTrigArgument(x, context.angleUnit(), position);
                yield AngleTrig.cos(x, context.angleUnit());
            }
            case "tan" -> {
                validateTrigArgument(x, context.angleUnit(), position);
                double radians = toRadians(x, context.angleUnit());
                if (Math.abs(Math.cos(radians)) < 1e-14) throw math("Tangent domain", position);
                yield Math.tan(radians);
            }
            case "asin" -> {
                if (Math.abs(x) > 1.0) throw math("asin domain", position);
                yield fromRadians(Math.asin(x), context.angleUnit());
            }
            case "acos" -> {
                if (Math.abs(x) > 1.0) throw math("acos domain", position);
                yield fromRadians(Math.acos(x), context.angleUnit());
            }
            case "atan" -> fromRadians(Math.atan(x), context.angleUnit());
            case "sinh" -> {
                if (Math.abs(x) > 230.2585092) throw math("sinh range", position);
                yield Math.sinh(x);
            }
            case "cosh" -> {
                if (Math.abs(x) > 230.2585092) throw math("cosh range", position);
                yield Math.cosh(x);
            }
            case "tanh" -> Math.tanh(x);
            case "asinh" -> {
                double magnitude = Math.abs(x);
                // Preserve odd symmetry without subtracting nearly equal large
                // numbers; log1p also retains arguments arbitrarily close to zero.
                double result = magnitude >= 1e8
                        ? Math.log(magnitude) + Math.log(2.0)
                        : Math.log1p(magnitude + magnitude * magnitude
                                / (1.0 + Math.hypot(magnitude, 1.0)));
                yield Math.copySign(result, x);
            }
            case "acosh" -> {
                if (x < 1.0) throw math("acosh domain", position);
                yield Math.log(x + Math.sqrt(x - 1.0) * Math.sqrt(x + 1.0));
            }
            case "atanh" -> {
                if (Math.abs(x) > 0.9999999999) throw math("atanh domain", position);
                yield 0.5 * Math.log((1.0 + x) / (1.0 - x));
            }
            case "sqrt" -> {
                if (x < 0.0) throw math("Square root domain", position);
                yield Math.sqrt(x);
            }
            case "cbrt" -> Math.cbrt(x);
            case "ln" -> {
                if (!(x > 0.0)) throw math("Natural log domain", position);
                yield Math.log(x);
            }
            case "exp" -> Math.exp(x);
            case "abs" -> Math.abs(x);
            case "rnd" -> context.round(x);
            case "floor" -> Math.floor(x);
            case "ceil" -> Math.ceil(x);
            case "sign" -> Math.signum(x);
            default -> throw argument("Unknown function " + function, position);
        };
    }

    private static void validateTrigArgument(double value, AngleUnit unit, int position) {
        double limit = switch (unit) {
            case DEG -> 9e9;
            case RAD -> 157079632.7;
            case GRAD -> 1e10;
        };
        if (Math.abs(value) >= limit) throw math("Trigonometric range", position);
    }

    private static double roundToDisplayPrecision(double value, RoundingPolicy policy) {
        if (value == 0.0) return 0.0;
        BigDecimal decimal = new BigDecimal(Double.toString(value));
        return switch (policy.kind()) {
            case FIX -> decimal.setScale(policy.digits(), RoundingMode.HALF_UP).doubleValue();
            case SCI -> decimal.round(new MathContext(policy.digits(), RoundingMode.HALF_UP))
                    .doubleValue();
            case NORM -> decimal.round(new MathContext(10, RoundingMode.HALF_UP)).doubleValue();
        };
    }

    private static ExactValue exactTrig(String function, ExactValue argument, AngleUnit unit) {
        Double degrees = exactDegrees(argument, unit);
        if (degrees == null) return null;
        double angle = normalizeDegrees(degrees);
        if (function.equals("cos")) angle = normalizeDegrees(angle + 90.0);
        ExactValue sine = exactSine(angle);
        if (!function.equals("tan")) return sine;
        ExactValue cosine = exactSine(normalizeDegrees(angle + 90.0));
        return sine == null || cosine == null || cosine.isZero() ? null : sine.divide(cosine);
    }

    private static ExactValue exactInverseTrig(String function, ExactValue argument,
                                               AngleUnit unit) {
        double value = argument.toDouble();
        Double degrees = null;
        if (function.equals("asin")) {
            if (near(value, -1.0)) degrees = -90.0;
            else if (near(value, -0.5)) degrees = -30.0;
            else if (near(value, 0.0)) degrees = 0.0;
            else if (near(value, 0.5)) degrees = 30.0;
            else if (near(value, Math.sqrt(0.5))) degrees = 45.0;
            else if (near(value, Math.sqrt(3.0) / 2.0)) degrees = 60.0;
            else if (near(value, 1.0)) degrees = 90.0;
        } else if (function.equals("acos")) {
            if (near(value, 1.0)) degrees = 0.0;
            else if (near(value, Math.sqrt(3.0) / 2.0)) degrees = 30.0;
            else if (near(value, Math.sqrt(0.5))) degrees = 45.0;
            else if (near(value, 0.5)) degrees = 60.0;
            else if (near(value, 0.0)) degrees = 90.0;
            else if (near(value, -0.5)) degrees = 120.0;
            else if (near(value, -1.0)) degrees = 180.0;
        } else if (function.equals("atan")) {
            if (near(value, -1.0)) degrees = -45.0;
            else if (near(value, 0.0)) degrees = 0.0;
            else if (near(value, 1.0)) degrees = 45.0;
            else if (near(value, Math.sqrt(3.0))) degrees = 60.0;
        }
        return degrees == null ? null : exactAngle(degrees, unit);
    }

    private static Double exactDegrees(ExactValue value, AngleUnit unit) {
        if (unit == AngleUnit.RAD) {
            Rational coefficient = value.rationalMultipleOfPi();
            return coefficient == null ? null : coefficient.toDouble() * 180.0;
        }
        Rational rational = value.rational();
        if (rational == null) return null;
        return unit == AngleUnit.DEG ? rational.toDouble() : rational.toDouble() * 0.9;
    }

    private static ExactValue exactAngle(double degrees, AngleUnit unit) {
        Rational degreeValue = Rational.approximate(degrees, 3600L, 1e-12);
        return switch (unit) {
            case DEG -> ExactValue.rational(degreeValue);
            case GRAD -> ExactValue.rational(degreeValue.multiply(new Rational(10))
                    .divide(new Rational(9)));
            case RAD -> ExactValue.PI.multiply(ExactValue.rational(degreeValue))
                    .divide(ExactValue.integer(180));
        };
    }

    private static ExactValue exactSine(double degrees) {
        if (near(degrees, 0.0) || near(degrees, 180.0)) return ExactValue.ZERO;
        if (near(degrees, 90.0)) return ExactValue.ONE;
        if (near(degrees, 270.0)) return ExactValue.ONE.negate();
        boolean negative = degrees > 180.0;
        double reference = degrees;
        if (reference > 180.0) reference -= 180.0;
        if (reference > 90.0) reference = 180.0 - reference;
        ExactValue result;
        if (near(reference, 30.0)) {
            result = ExactValue.rational(new Rational(1).divide(new Rational(2)));
        } else if (near(reference, 45.0)) {
            result = ExactValue.sqrt(new Rational(2)).divide(ExactValue.integer(2));
        } else if (near(reference, 60.0)) {
            result = ExactValue.sqrt(new Rational(3)).divide(ExactValue.integer(2));
        } else {
            return null;
        }
        return negative ? result.negate() : result;
    }

    private static double normalizeDegrees(double degrees) {
        double result = degrees % 360.0;
        return result < 0.0 ? result + 360.0 : result;
    }

    private static boolean near(double left, double right) {
        return Math.abs(left - right) <= 1e-11;
    }

    private static double toRadians(double value, AngleUnit unit) {
        return switch (unit) {
            case DEG -> Math.toRadians(value);
            case RAD -> value;
            case GRAD -> value * Math.PI / 200.0;
        };
    }

    private static double fromRadians(double value, AngleUnit unit) {
        return switch (unit) {
            case DEG -> Math.toDegrees(value);
            case RAD -> value;
            case GRAD -> value * 200.0 / Math.PI;
        };
    }

    public static final class SyntaxException extends CalculationException {
        public SyntaxException(String message, int position) {
            super(CalculationError.SYNTAX, message, position);
        }
    }
}

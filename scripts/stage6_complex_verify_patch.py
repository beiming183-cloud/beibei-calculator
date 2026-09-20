from pathlib import Path

engine_path = Path('core/src/main/java/com/codex/fx991/core/math/ComplexExpressionEngine.java')
engine = engine_path.read_text(encoding='utf-8')
old = '''    public static ComplexValue evaluate(
            String expression, Map<String, ComplexValue> variables, ComplexValue ans, AngleUnit angleUnit) {
        Parser parser = new Parser(expression, variables == null ? com.codex.fx991.core.Compat.emptyMap() : variables,
                ans == null ? ComplexValue.ZERO : ans, angleUnit == null ? AngleUnit.DEG : angleUnit);
        ComplexValue value = parser.parseExpression();
        parser.skip();
        if (!parser.end()) throw new IllegalArgumentException("Syntax ERROR at " + parser.position);
        return value;
    }
'''
new = old + '''
    /** Verifies one top-level relation in Complex mode. Complex ordered relations are invalid. */
    public static boolean verify(String expression, Map<String, ComplexValue> variables,
                                 ComplexValue ans, AngleUnit angleUnit) {
        RelationCall relation = relationCall(expression);
        ComplexValue left = evaluate(relation.left(), variables, ans, angleUnit);
        ComplexValue right = evaluate(relation.right(), variables, ans, angleUnit);
        return switch (relation.operator()) {
            case "=" -> left.approximatelyEquals(right, 1e-10);
            case "!=" -> !left.approximatelyEquals(right, 1e-10);
            case "<", "<=", ">", ">=" -> {
                if (Math.abs(left.imaginary()) > 1e-12 || Math.abs(right.imaginary()) > 1e-12) {
                    throw new ArithmeticException("Complex inequality");
                }
                yield switch (relation.operator()) {
                    case "<" -> left.real() < right.real();
                    case "<=" -> left.real() <= right.real();
                    case ">" -> left.real() > right.real();
                    default -> left.real() >= right.real();
                };
            }
            default -> throw new IllegalArgumentException("Unknown relation");
        };
    }

    private static RelationCall relationCall(String expression) {
        String source = expression == null ? "" : expression.trim();
        int depth = 0;
        int relationIndex = -1;
        int relationWidth = 0;
        String operator = null;
        for (int index = 0; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '(') { depth++; continue; }
            if (value == ')') { depth--; if (depth < 0) throw new IllegalArgumentException("Syntax ERROR"); continue; }
            if (depth != 0) continue;
            String candidate = null;
            int width = 1;
            if (index + 1 < source.length()) {
                String two = source.substring(index, index + 2);
                if (two.equals("!=") || two.equals("<=") || two.equals(">=")) {
                    candidate = two;
                    width = 2;
                }
            }
            if (candidate == null) {
                candidate = switch (value) {
                    case '=' -> "=";
                    case '≠' -> "!=";
                    case '<' -> "<";
                    case '≤' -> "<=";
                    case '>' -> ">";
                    case '≥' -> ">=";
                    default -> null;
                };
            }
            if (candidate == null) continue;
            if (operator != null) throw new IllegalArgumentException("Multiple relational operators");
            operator = candidate;
            relationIndex = index;
            relationWidth = width;
            index += width - 1;
        }
        if (depth != 0 || operator == null) throw new IllegalArgumentException("Relational operator required");
        String left = source.substring(0, relationIndex).trim();
        String right = source.substring(relationIndex + relationWidth).trim();
        if (left.isEmpty() || right.isEmpty()) throw new IllegalArgumentException("Relation side missing");
        return new RelationCall(left, operator, right);
    }

    private record RelationCall(String left, String operator, String right) {}
'''
if old not in engine:
    raise SystemExit('ComplexExpressionEngine insertion target not found')
engine = engine.replace(old, new, 1)
engine_path.write_text(engine, encoding='utf-8')

machine_path = Path('core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java')
machine = machine_path.read_text(encoding='utf-8')
old_verify = '''            if (verificationMode && application == ApplicationMode.CALCULATE) {
                boolean valid = ScalarExpressionEngine.verify(source, evaluationContext());
                formatted = valid ? "True" : "False";
                scalar = valid ? 1.0 : 0.0;
                exactScalar = ExactValue.integer(valid ? 1 : 0);
            } else if (application == ApplicationMode.CALCULATE
'''
new_verify = '''            if (verificationMode && application == ApplicationMode.CALCULATE) {
                boolean valid = ScalarExpressionEngine.verify(source, evaluationContext());
                formatted = valid ? "True" : "False";
                scalar = valid ? 1.0 : 0.0;
                exactScalar = ExactValue.integer(valid ? 1 : 0);
            } else if (verificationMode && application == ApplicationMode.COMPLEX) {
                Map<String, ComplexValue> complexVariables = new HashMap<>();
                for (Map.Entry<String, Double> entry : variables.entrySet()) {
                    complexVariables.put(entry.getKey(), new ComplexValue(entry.getValue(), 0.0));
                }
                boolean valid = ComplexExpressionEngine.verify(plainSource, complexVariables,
                        hasComplexAns ? complexAns
                                : hasAns ? new ComplexValue(ans, 0.0) : ComplexValue.ZERO,
                        settings.angleUnit());
                formatted = valid ? "True" : "False";
                scalar = valid ? 1.0 : 0.0;
                exactScalar = ExactValue.integer(valid ? 1 : 0);
                storeAnswer = false;
            } else if (application == ApplicationMode.CALCULATE
'''
if old_verify not in machine:
    raise SystemExit('machine verify branch target not found')
machine = machine.replace(old_verify, new_verify, 1)
old_commit = '''            commitSuccessfulResult(formatted, scalar, exactScalar, completionStatus,
                    storeAnswer, complexEvaluation);'''
new_commit = '''            commitSuccessfulResult(formatted, scalar, exactScalar, completionStatus,
                    storeAnswer, complexEvaluation && !verificationMode);'''
if old_commit not in machine:
    raise SystemExit('commit call target not found')
machine = machine.replace(old_commit, new_commit, 1)
old_tools = '''            case TOOLS -> com.codex.fx991.core.Compat.list(command("undo", "撤消", "恢复上次编辑"),
                    command("simplify", "化简", manualSimplification ? "手动" : "自动"),
                    command("verify", "运算验证", verificationMode ? "开" : "关"));'''
new_tools = '''            case TOOLS -> toolCommands();'''
if old_tools not in machine:
    raise SystemExit('tools menu target not found')
machine = machine.replace(old_tools, new_tools, 1)
marker = '''    private List<CnCwCommand> catalogRootCommands() {'''
helper = '''    private List<CnCwCommand> toolCommands() {
        List<CnCwCommand> items = new ArrayList<>(com.codex.fx991.core.Compat.list(
                command("undo", "撤消", "恢复上次编辑"),
                command("simplify", "化简", manualSimplification ? "手动" : "自动")));
        // Function-table and equation verification need their dedicated answer-entry
        // workflows; until those are core-owned, do not expose a non-functional toggle.
        if (application == ApplicationMode.CALCULATE || application == ApplicationMode.COMPLEX) {
            items.add(command("verify", "运算验证", verificationMode ? "开" : "关"));
        }
        return com.codex.fx991.core.Compat.copyList(items);
    }

'''
if marker not in machine:
    raise SystemExit('toolCommands insertion marker not found')
machine = machine.replace(marker, helper + marker, 1)
machine_path.write_text(machine, encoding='utf-8')

test_path = Path('core/src/regression/java/com/codex/fx991/core/CnCwCompletenessSuite.java')
test = test_path.read_text(encoding='utf-8')
old_run = '''        formatterSettingsChangeRealResults();
        System.out.println("PASS " + checks + " completeness checks");'''
new_run = '''        formatterSettingsChangeRealResults();
        complexVerificationUsesComplexRelations();
        verificationToggleIsNotExposedAsFakeFunctionality();
        System.out.println("PASS " + checks + " completeness checks");'''
if old_run not in test:
    raise SystemExit('completeness run target not found')
test = test.replace(old_run, new_run, 1)
marker = '''    private static CnCwMachine openApplication(int homeIndex) {'''
methods = '''    private void complexVerificationUsesComplexRelations() {
        CnCwMachine machine = openApplication(5);
        machine.dispatch(CnCwKey.TOOLS);
        moveDown(machine, 2);
        machine.dispatch(CnCwKey.OK);
        check(machine.state().verificationMode(), "Complex app enables verification");
        machine.pasteExpression("i^2=-1");
        machine.dispatch(CnCwKey.EXE);
        equal("True", machine.state().result(), "complex equality i^2=-1 verifies true");

        machine.dispatch(CnCwKey.AC);
        machine.pasteExpression("i=1");
        machine.dispatch(CnCwKey.EXE);
        equal("False", machine.state().result(), "incorrect complex equality verifies false");

        machine.dispatch(CnCwKey.AC);
        machine.pasteExpression("i<2");
        machine.dispatch(CnCwKey.EXE);
        check(machine.state().calculationState().isError(),
                "ordered relation containing a complex value is rejected");
        equal(CalculationError.MATH, machine.state().calculationState().error(),
                "complex inequality uses Math ERROR");
    }

    private void verificationToggleIsNotExposedAsFakeFunctionality() {
        int[] supportedNow = {0, 5};
        for (int homeIndex : supportedNow) {
            CnCwMachine machine = openApplication(homeIndex);
            machine.dispatch(CnCwKey.TOOLS);
            check(commandIndex(machine.state().menuItems(), "verify") >= 0,
                    "working verification toggle is exposed for app " + homeIndex);
        }
        int[] pendingDedicatedWorkflow = {1, 2, 3, 4, 6, 7, 8, 9};
        for (int homeIndex : pendingDedicatedWorkflow) {
            CnCwMachine machine = openApplication(homeIndex);
            machine.dispatch(CnCwKey.TOOLS);
            check(commandIndex(machine.state().menuItems(), "verify") < 0,
                    "unsupported verification is hidden for app " + homeIndex);
        }
    }

'''
if marker not in test:
    raise SystemExit('completeness insertion marker not found')
test = test.replace(marker, methods + marker, 1)
test_path.write_text(test, encoding='utf-8')

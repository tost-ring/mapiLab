package app.model.variable;

import jorg.processor.IntProcessor;
import jorg.processor.ProcessorException;
import suite.suite.Slot;
import suite.suite.Subject;
import suite.suite.Suite;
import suite.suite.action.Action;
import suite.suite.action.Impression;

public class ExpressionProcessor implements IntProcessor {

    enum State {
        PENDING, NUMBER, SYMBOL
    }

    static abstract class ActionProfile {
        Action action;
        String name;

        public ActionProfile(Impression impression, String name) {
            this.action = impression;
            this.name = name;
        }

        public ActionProfile(Action action, String name) {
            this.action = action;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        public abstract boolean pushes(ActionProfile that);
    }

    static class PrefixActionProfile extends ActionProfile {

        public PrefixActionProfile(Impression impression, String name) {
            super(impression, name);
        }

        public PrefixActionProfile(Action action, String name) {
            super(action, name);
        }

        @Override
        public boolean pushes(ActionProfile that) {
            return false;
        }
    }

    static class InfixActionProfile extends ActionProfile {
        final int priority;

        public InfixActionProfile(int priority, Impression impression, String name) {
            super(impression, name);
            this.priority = priority;
        }

        public InfixActionProfile(int priority, Action action, String name) {
            super(action, name);
            this.priority = priority;
        }

        public boolean pushes(ActionProfile that) {
            if(that instanceof InfixActionProfile)return priority <= ((InfixActionProfile) that).priority;
            return true;
        }
    }

    static class PostfixActionProfile extends ActionProfile {

        public PostfixActionProfile(Impression impression, String name) {
            super(impression, name);
        }

        public PostfixActionProfile(Action action, String name) {
            super(action, name);
        }

        public boolean pushes(ActionProfile that) {
            if(that instanceof InfixActionProfile)return false;
            return true;
        }
    }

    static class FunctionProfile extends ActionProfile {

        public FunctionProfile(String name) {
            super(null, name);
        }

        public FunctionProfile(Action action, String name) {
            super(action, name);
        }

        public boolean pushes(ActionProfile that) {
            return false;
        }
    }

    static class VarNumber extends Number {
        final String symbol;
        Number value;

        public VarNumber(String symbol) {
            this.symbol = symbol;
        }

        void setValue(Number value) {
            this.value = value;
        }

        @Override
        public int intValue() {
            return value.intValue();
        }

        @Override
        public long longValue() {
            return value.longValue();
        }

        @Override
        public float floatValue() {
            return value.floatValue();
        }

        @Override
        public double doubleValue() {
            return value.doubleValue();
        }

        @Override
        public String toString() {
            return symbol + " = " + value;
        }
    }

    enum SpecialSymbol {
        OPEN_BRACKET, CLOSE_BRACKET, SPLINE
    }

    private static final ActionProfile attribution = new InfixActionProfile(0, ExpressionProcessor::attribution, "=");
    private static final ActionProfile postAttribution = new InfixActionProfile(0, ExpressionProcessor::postAttribution, "->");
    private static final ActionProfile maximum = new InfixActionProfile(1, ExpressionProcessor::maximum, "|");
    private static final ActionProfile minimum = new InfixActionProfile(1, ExpressionProcessor::minimum, "&");
    private static final ActionProfile addition = new InfixActionProfile(2, ExpressionProcessor::addition, "+");
    private static final ActionProfile subtraction = new InfixActionProfile(2, ExpressionProcessor::subtraction, "-");
    private static final ActionProfile multiplication = new InfixActionProfile(3, ExpressionProcessor::multiplication, "*");
    private static final ActionProfile division = new InfixActionProfile(3, ExpressionProcessor::division, "/");
    private static final ActionProfile exponentiation = new InfixActionProfile(4, ExpressionProcessor::exponentiation, "^");
    private static final ActionProfile reversion = new PrefixActionProfile(ExpressionProcessor::reversion, "0-");
    private static final ActionProfile inversion = new PrefixActionProfile(ExpressionProcessor::inversion, "1/");
    private static final ActionProfile proportion = new PostfixActionProfile(ExpressionProcessor::proportion, "%");
    private static final ActionProfile absolution = new PrefixActionProfile(ExpressionProcessor::absolution, "||");

    private static final Subject descriptiveActionProfiles = Suite.
            set("sin", (Action)Exp::sin).
            set("cos", (Action)Exp::cos).
            set("max", (Action)Exp::max).
            set("min", (Action)Exp::min).
            set("sum", (Action)Exp::sum);


    private Subject functions;
    private Subject inputs;
    private Subject outputs;

    private Subject actions;
    private StringBuilder builder;
    private State state;
    private Subject rpn;
    private boolean emptyValueBuffer;
    private int automaticOutput;

    @Override
    public Subject ready() {
        inputs = Suite.set();
        functions = Suite.set();
        outputs = Suite.set();
        rpn = Suite.set();
        actions = Suite.set();
        state = State.PENDING;
        emptyValueBuffer = true;
        automaticOutput = 0;
        return Suite.set();
    }

    private void pushAction(ActionProfile profile) {
        for(var s : actions.reverse()) {
            if(s.assigned(ActionProfile.class)) {
                ActionProfile actionProfile = s.asExpected();
                if(profile.pushes(actionProfile)) {
                    rpn.add(actionProfile);
                    actions.unset(s.key().direct());
                } else break;
            } else if(s.direct() == SpecialSymbol.SPLINE) {
                break;
            } else {
                rpn.add(s.direct());
                actions.unset(s.key().direct());
            }
        }
        actions.add(profile);
        emptyValueBuffer = true;
    }

    @Override
    public void advance(int i) throws ProcessorException {
        switch (state) {
            case PENDING:
                if(Character.isDigit(i)) {
                    builder = new StringBuilder();
                    builder.appendCodePoint(i);
                    state = State.NUMBER;
                } else if(Character.isJavaIdentifierStart(i)) {
                    builder = new StringBuilder();
                    builder.appendCodePoint(i);
                    state = State.SYMBOL;
                } else if(i == '+') {
                    pushAction(emptyValueBuffer ? absolution : addition);
                } else if(i == '-') {
                    pushAction(emptyValueBuffer ? reversion : subtraction);
                } else if(i == '*') {
                    if(!emptyValueBuffer) pushAction(multiplication);
                } else if(i == '/') {
                    pushAction(emptyValueBuffer ? inversion : division);
                } else if(i == '^') {
                    pushAction(exponentiation);
                } else if(i == '%') {
                    rpn.add(proportion);
                } else if(i == '&') {
                    pushAction(minimum);
                } else if(i == '|') {
                    pushAction(maximum);
                }   else if(i == '=') {
                    VarNumber var = rpn.recent().asExpected();
                    outputs.put(var.symbol, var);
                    if(emptyValueBuffer)rpn.add(var);
                    pushAction(attribution);
                } else if(i == '(') {
                    actions.add(SpecialSymbol.SPLINE);
                    rpn.add(SpecialSymbol.OPEN_BRACKET);
                    emptyValueBuffer = true;
                } else if(i == ')') {
                    for(var s : actions.reverse()) {
                        actions.unset(s.key().direct());
                        if(s.direct() == SpecialSymbol.SPLINE) {
                            break;
                        } else {
                            rpn.add(s.direct());
                        }
                    }
                    rpn.add(SpecialSymbol.CLOSE_BRACKET);
                } else if(i == ',') {
                    for(var s : actions.reverse()) {
                        if(s.direct() == SpecialSymbol.SPLINE) {
                            break;
                        } else {
                            rpn.add(s.direct());
                            actions.unset(s.key().direct());
                        }
                    }
                    if(rpn.recent().direct() != attribution) {
                        VarNumber var = new VarNumber("" + automaticOutput++);
                        outputs.put(var.symbol, var);
                        rpn.add(var).add(postAttribution);
                    }
                    emptyValueBuffer = true;
                } else if(i == ';') {
                    int brackets = 0;
                    for(var s : rpn.reverse()) {
                        if(s.direct() == SpecialSymbol.OPEN_BRACKET) {
                            if (--brackets < 0) break;
                        } else if(s.direct() == SpecialSymbol.CLOSE_BRACKET) {
                            ++brackets;
                        }
                        actions.add(s.direct());
                        rpn.unset(s.key().direct());
                    }
                    actions.add(SpecialSymbol.SPLINE);
                    emptyValueBuffer = true;
                }/* else if(i == '`') {
                    builder = new StringBuilder();
                    state = State.SYMBOL;
                }*/ else if(!Character.isWhitespace(i)) {
                    throw new ProcessorException();
                }
                break;
            case NUMBER:
                if(Character.isDigit(i) || i == '.') {
                    builder.appendCodePoint(i);
                } else if(!Character.isWhitespace(i)) {
                    try {
                        double d = Double.parseDouble(builder.toString());
                        rpn.add(d);
                        state = State.PENDING;
                        emptyValueBuffer = false;
                        advance(i);
                    } catch (NumberFormatException nfe) {
                        throw new ProcessorException(nfe);
                    }
                }
                break;
            case SYMBOL:
                if(Character.isJavaIdentifierPart(i)) {
                    builder.appendCodePoint(i);
                } else if(i == '(') {
                    String str = builder.toString();
                    pushAction(functions.getSaved(str, new FunctionProfile(str + "()")).asExpected());
                    actions.add(SpecialSymbol.SPLINE);
                    rpn.add(SpecialSymbol.OPEN_BRACKET);
                    state = State.PENDING;
                } else if(!Character.isWhitespace(i)) {
                    VarNumber var = new VarNumber(builder.toString());
                    Subject in = inputs.get(var.symbol);
                    if(in.settled()) var = in.asExpected();
                    else if(i != '=') inputs.set(var.symbol, var);
                    rpn.add(var);
                    emptyValueBuffer = false;
                    state = State.PENDING;
                    advance(i);
                }
                break;
        }
    }

    @Override
    public Subject finish() throws ProcessorException {
        switch (state) {
            case NUMBER -> {
                try {
                    double d = Double.parseDouble(builder.toString());
                    rpn.add(d);
                } catch (NumberFormatException nfe) {
                    throw new ProcessorException(nfe);
                }
            }
            case SYMBOL -> {
                VarNumber var = new VarNumber(builder.toString());
                var = inputs.getSaved(var.symbol, var).asExpected();
                rpn.add(var);
            }
        }
        for(var s : actions.reverse()) {
            if(s.direct() != SpecialSymbol.SPLINE) {
                rpn.add(s.direct());
            }
        }
        if(rpn.recent().direct() != attribution) {
            VarNumber var = new VarNumber("" + automaticOutput);
            outputs.put(var.symbol, var);
            rpn.add(var).add(postAttribution);
        }
//        System.out.println(rpn);
        return Suite.set(new Exp(inputs, outputs) {
            @Override
            public Subject play(Subject subject) {
                for (var v : inputs.values().filter(VarNumber.class)) {
                    Object o = subject.get(v.symbol).orGiven(null);
                    if(o instanceof Number) v.value = (Number)o;
                    else if(o instanceof Boolean) v.value = (Boolean) o ? 1 : -1;
                }
                for (var f : functions) {
                    String funName = f.key().asString();
                    Subject s1 = subject.get(funName);
                    if (s1.settled()) f.asGiven(FunctionProfile.class).action = s1.asExpected();
                    else {
                        s1 = descriptiveActionProfiles.get(funName);
                        if (s1.settled()) f.asGiven(FunctionProfile.class).action = s1.asExpected();
                        else throw new RuntimeException("Function '" + funName + "' is not defined");
                    }
                }
                Subject bracketStack = Suite.set();
                Subject result = Suite.set();
                for (var su : rpn) {
                    if (su.assigned(Number.class)) {
                        result.add(su.direct());
                    } else if (su.assigned(ActionProfile.class)) {
                        if (su.assigned(FunctionProfile.class)) {
                            Subject p = Suite.set();
                            for (var s1 : result.reverse()) {
                                if (s1.direct() == bracketStack.recent().direct()) {
                                    bracketStack.unset(bracketStack.recent().key().direct());
                                    break;
                                }
                                result.unset(s1.key().direct());
                                p.addAt(Slot.PRIME, s1.direct());
                            }
                            p = su.asGiven(FunctionProfile.class).action.play(p);
                            result.addAll(p.values());
                        } else {
                            su.asGiven(ActionProfile.class).action.play(result);
                        }
                    } else if(su.direct() == SpecialSymbol.OPEN_BRACKET){
                        bracketStack.inset(result.recent());
                    }
                }
                return outputs.map(so -> Suite.set(so.key().direct(), so.asGiven(VarNumber.class).doubleValue())).set();
            }
        });
    }

    protected static void addition(Subject s) {
        double a = s.takeAt(Slot.RECENT).asDouble();
        double b = s.takeAt(Slot.RECENT).asDouble();
        s.add(b + a);
    }

    protected static void subtraction(Subject s) {
        double a = s.takeAt(Slot.RECENT).asDouble();
        double b = s.takeAt(Slot.RECENT).asDouble();
        s.add(b - a);
    }

    protected static void multiplication(Subject s) {
        double a = s.takeAt(Slot.RECENT).asDouble();
        double b = s.takeAt(Slot.RECENT).asDouble();
        s.add(b * a);
    }

    protected static void division(Subject s) {
        double a = s.takeAt(Slot.RECENT).asDouble();
        double b = s.takeAt(Slot.RECENT).asDouble();
        s.add(b / a);
    }

    protected static void exponentiation(Subject s) {
        double a = s.takeAt(Slot.RECENT).asDouble();
        double b = s.takeAt(Slot.RECENT).asDouble();
        s.add(Math.pow(b, a));
    }

    protected static void proportion(Subject s) {
        double a = s.takeAt(Slot.RECENT).asDouble();
        s.add(a / 100.0);
    }

    protected static void attribution(Subject s) {
        double a = s.takeAt(Slot.RECENT).asDouble();
        VarNumber var = s.recent().asExpected();
        var.value = a;
    }

    protected static void postAttribution(Subject s) {
        VarNumber var = s.takeAt(Slot.RECENT).asExpected();
        var.value = s.takeAt(Slot.RECENT).asDouble();
    }

    protected static void inversion(Subject s) {
        double a = s.takeAt(Slot.RECENT).asDouble();
        s.add(1 / a);
    }

    protected static void reversion(Subject s) {
        double a = s.takeAt(Slot.RECENT).asDouble();
        s.add(-a);
    }

    protected static void absolution(Subject s) {
        double a = s.takeAt(Slot.RECENT).asDouble();
        s.add(Math.abs(a));
    }

    protected static void maximum(Subject s) {
        double a = s.takeAt(Slot.RECENT).asDouble();
        double b = s.takeAt(Slot.RECENT).asDouble();
        s.add(Math.max(a, b));
    }

    protected static void minimum(Subject s) {
        double a = s.takeAt(Slot.RECENT).asDouble();
        double b = s.takeAt(Slot.RECENT).asDouble();
        s.add(Math.min(a, b));
    }
}

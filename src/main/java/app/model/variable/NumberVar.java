package app.model.variable;

import suite.suite.Subject;
import suite.suite.Suite;
import suite.suite.action.Action;
import suite.suite.util.Fluid;

import java.lang.ref.WeakReference;
import java.util.function.Function;

public final class NumberVar extends Var<Number> {

    public static NumberVar emit() {
        return new NumberVar(null, false);
    }

    public static NumberVar emit(Number value) {
        return new NumberVar(value, false);
    }

    public static NumberVar emit(Number value, boolean instant) {
        return new NumberVar(value, instant);
    }

    public static NumberVar assigned(Object that) {
        NumberVar v = new NumberVar(null, false);
        Fun.assign(that, v);
        return v;
    }

    public static NumberVar compound(Number value, Fluid components, Action recipe, Object resultKey) {
        NumberVar composite = new NumberVar(value, false);
        Fun.compose(ValueProducer.prepareComponents(components, composite), Suite.set(resultKey, composite), recipe);
        return composite;
    }

    public static NumberVar compound(Fluid components, Action recipe, Object resultKey) {
        NumberVar composite = new NumberVar(null, false);
        Fun.compose(ValueProducer.prepareComponents(components, composite), Suite.set(resultKey, composite), recipe).press(true);
        return composite;
    }


    public static NumberVar compound(Number value, Fluid components, Function<Subject, Number> recipe) {
        NumberVar composite = new NumberVar(value, false);
        Fun.compose(ValueProducer.prepareComponents(components, composite), Suite.set(OWN_VALUE, composite),
                s -> Suite.set(OWN_VALUE, recipe.apply(s)));
        return composite;
    }

    public static NumberVar compound(Fluid components, Function<Subject, Number> recipe) {
        NumberVar composite = new NumberVar(null, false);
        Fun.compose(ValueProducer.prepareComponents(components, composite), Suite.set(OWN_VALUE, composite),
                s -> Suite.set(OWN_VALUE, recipe.apply(s))).press(true);
        return composite;
    }

    public static NumberVar expressed(Fluid components, Exp expression) {
        NumberVar composite = new NumberVar(null, false);
        BeltFun.express(ValueProducer.prepareComponents(components, composite),
                Suite.add(composite), expression).press(true);
        return composite;
    }

    public static NumberVar expressed(Fluid components, String expression) {
        NumberVar composite = new NumberVar(null, false);
        BeltFun.express(ValueProducer.prepareComponents(components, composite),
                Suite.add(composite), expression).press(true);
        return composite;
    }

    public static NumberVar expressed(Number value, Fluid components, Action recipe) {
        NumberVar composite = new NumberVar(value, false);
        BeltFun.compose(ValueProducer.prepareComponents(components, composite), Suite.set(composite), recipe);
        return composite;
    }

    public static NumberVar expressed(Fluid components, Action recipe) {
        NumberVar composite = new NumberVar(null, false);
        BeltFun.compose(ValueProducer.prepareComponents(components, composite), Suite.set(composite), recipe).press(true);
        return composite;
    }

    public static NumberVar expressed(String e, Object ... params) {
        return expressed( Playground.abc(params), e);
    }

    public static NumberVar expressed(String e, Fluid params) {
        return expressed(params, e);
    }

    public static NumberVar add(Object a, Object b) {
        return expressed( Playground.abc(a, b), Exp::add);
    }

    public static NumberVar sum(Object ... o) {
        return expressed( Playground.abc(o), Exp::sum);
    }

    public static NumberVar difference(Object a, Object b) {
        return expressed( Playground.abc(a, b), Exp::sub);
    }

    public static NumberVar or(Object a, Object b) {
        return expressed( Playground.abc(a, b), Exp::max);
    }

    public static NumberVar or(Object ... o) {
        return expressed( Playground.abc(o), Exp::max);
    }

    public static NumberVar and(Object a, Object b) {
        return expressed( Playground.abc(a, b), Exp::min);
    }

    public static NumberVar and(Object ... o) {
        return expressed( Playground.abc(o), Exp::min);
    }

    public static NumberVar negate(Object ... o) {
        return expressed( Playground.abc(o), Exp::rev);
    }

    Number value;

    public NumberVar(Object value, boolean instant) {
        super(instant);
        value(value);
    }

    @Override
    Number value() {
        return value;
    }

    void value(Object v) {
        if(v instanceof Number) this.value = (Number) v;
        else if(v instanceof Boolean) this.value = (Boolean) v ? 1 : -1;
        else this.value = null;
    }

    @Override
    public Number get() {
        inspect();
        return value;
    }

    public Number get(Fun fun) {
        return get();
    }

    @Override
    public void set(Object value) {
        value(value);
        for(var s : outputs) {
            WeakReference<Fun> ref = s.asExpected();
            Fun fun = ref.get();
            if(fun != null) {
                fun.press(true);
            }
        }
    }

    @Override
    public void set(Object value, Fun fun) {
        if(value instanceof Number) this.value = (Number) value;
        else if(value instanceof Boolean) this.value = (Boolean) value ? 1 : -1;
        else this.value = null;
        if(detections != null)detections.unset(fun); // Jeśli wywołana w gałęzi równoległej, oznacz jako wykonana.
        for(var s : outputs) {
            WeakReference<Fun> ref = s.asExpected();
            Fun f = ref.get();
            if(f != null && f != fun) {
                f.press(true);
            }
        }
    }

    public byte getByte() {
        return get().byteValue();
    }

    public short getShort() {
        return get().shortValue();
    }

    public int getInt() {
        return get().intValue();
    }

    public long getLong() {
        return get().longValue();
    }

    public float getFloat() {
        return get().floatValue();
    }

    public double getDouble() {
        return get().doubleValue();
    }

    public boolean getBoolean(boolean trueOn0) {
        double d = get().doubleValue();
        return d > 0 || (d == .0 && trueOn0);
    }
}

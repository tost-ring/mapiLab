package app.model.variable;

import suite.suite.Subject;
import suite.suite.Suite;
import suite.suite.action.Action;
import suite.suite.util.Fluid;

import java.lang.ref.WeakReference;
import java.util.function.BiPredicate;
import java.util.function.Function;

public class Var<T> implements ValueProducer<T>, ValueConsumer<T> {
    /**
     * Leniwa implementacja (instant = false) czeka z wywołaniem funkcji do czasu użycia get().
     * Stan zmiennych położonych wyżej jest zawsze brany ostatni przed użyciem get(), a funkcje wywoływane tylko raz.
     *
     * Jeśli każdy stan zmiennych wyższych ma zostać zarejestrowany, należy użyć implementacji instant = true.
     */

    T value;
    Subject inputs = Suite.set();
    Subject outputs = Suite.set();
    Subject detections;

    public Var(T value, boolean instant) {
        this.value = value;
        if(!instant)detections = Suite.set();
    }

    public T get() {
        if(detections != null && detections.settled()) {
            detections.values().filter(Fun.class).forEach(Fun::execute);
            detections = Suite.set();
        }
        return value;
    }

    public T get(Fun fun) {
        return get();
    }

    public void set(T value) {
        this.value = value;
        for(var s : outputs) {
            WeakReference<Fun> ref = s.asExpected();
            Fun fun = ref.get();
            if(fun != null) {
                fun.press(true);
            }
        }
    }

    public void set(T value, Fun fun) {
        this.value = value;
        if(detections != null)detections.unset(fun); // Jeśli wywołana w gałęzi równoległej, oznacz jako wykonana.
        for(var s : outputs) {
            WeakReference<Fun> ref = s.asExpected();
            Fun f = ref.get();
            if(f != null && f != fun) {
                f.press(true);
            }
        }
    }

    public boolean press(Fun fun) {
        if(detections == null) {
            fun.execute();
            return true;
        } else {
            boolean pressOutputs = detections.desolated();
            detections.put(fun);
            if(pressOutputs) {
                for(var s : outputs) {
                    WeakReference<Fun> ref = s.asExpected();
                    Fun f = ref.get();
                    if(f != null && f != fun && f.press(false)) return true;
                }
            }
            return false;
        }
    }

    public boolean attachOutput(Fun fun) {
        outputs.put(new WeakReference<>(fun));
        return detections != null && detections.settled();
    }

    public void detachOutput(Fun fun) {
        for(var s : outputs) {
            WeakReference<Fun> ref = s.asExpected();
            Fun f = ref.get();
            if(f == null || f == fun) {
                outputs.unset(ref);
            }
        }
    }

    public void attachInput(Fun fun) {
        inputs.set(fun);
    }

    public void detachInput(Fun fun) {
        inputs.unset(fun);
        fun.detachOutputVar(this);
    }

    public void detachInputs() {
        inputs.keys().filter(Fun.class).forEach(this::detachInput);

    }

    public void detachOutputs() {
        outputs = Suite.set();
    }

    public void detach() {
        detachOutputs();
        detachInputs();
    }

    boolean cycleTest(Fun fun) {
        for(var s : outputs) {
            WeakReference<Fun> ref = s.asExpected();
            Fun f = ref.get();
            if(f == null){
                outputs.unset(s.key().direct());
            } else if(f == fun || f.cycleTest(fun)) return true;
        }
        return false;
    }

    public boolean isInstant() {
        return detections == null;
    }

    public Var<T> select(BiPredicate<T, T> selector) {
        return suppress(selector.negate());
    }

    public Var<T> suppress(BiPredicate<T, T> suppressor) {
        Var<T> suppressed = new Var<>(value, true);
        Fun.suppress(this, suppressed, suppressor);
        return suppressed;
    }

    public Var<T> suppressIdentity() {
        Var<T> suppressed = new Var<>(value, true);
        Fun.suppressIdentity(this, suppressed);
        return suppressed;
    }

    public Var<T> suppressEquality() {
        Var<T> suppressed = new Var<>(value, true);
        Fun.suppressEquality(this, suppressed);
        return suppressed;
    }

    public<V extends T> Var<T> assign(ValueProducer<V> vp) {
        Fun.assign(vp, this).press(true);
        return this;
    }

    public<V extends T> Var<T> assign(Subject sub) {
        if(sub.settled()) {
            Fun fun = new Fun(sub, Suite.set(Var.OWN_VALUE, this), s -> Suite.set(Var.OWN_VALUE, s.direct()));
            fun.reduce(true);
        }
        return this;
    }

    public Fun compose(Fluid components, Action recipe, Object resultKey) {
        return Fun.compose(ValueProducer.prepareComponents(components, this), Suite.set(resultKey, this), recipe);
    }

    public Fun compose(Fluid components, Function<Subject, T> recipe) {
        return Fun.compose(ValueProducer.prepareComponents(components, this), Suite.set(OWN_VALUE, this),
                s -> Suite.set(OWN_VALUE, recipe.apply(s)));
    }

    public BeltFun express(Fluid components, Exp expression) {
        return BeltFun.express(ValueProducer.prepareComponents(components, this),
                Suite.add(this), expression);
    }

    private BeltFun express(Fluid components, String expression) {
        return BeltFun.express(ValueProducer.prepareComponents(components, this),
                Suite.add(this), expression);
    }

    public BeltFun express(Fluid components, Action recipe) {
        return BeltFun.compose(ValueProducer.prepareComponents(components, this), Suite.set(this), recipe);
    }

    public WeakVar<T> weak() {
        return new WeakVar<>(this);
    }

    @Override
    public String toString() {
        if(detections != null && detections.settled())
            return "(" + value + ")";
        else return "<" + value + ">";
    }

    public Subject getInputs() {
        return inputs;
    }
}

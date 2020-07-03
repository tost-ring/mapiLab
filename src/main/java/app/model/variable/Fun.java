package app.model.variable;

import suite.suite.Subject;
import suite.suite.Suite;
import suite.suite.action.Action;

import java.util.Objects;
import java.util.function.BiPredicate;

public class Fun {

    public static class Const {}

    public static final Const SELF = new Const();

    public static Fun create(Subject inputs, Subject outputs, Action transition) {
        return new Fun(inputs, outputs, transition);
    }

    public static<T, T1 extends T> Fun assign(Var<T1> source, Var<T> target) {
        return new Fun(Suite.set(source), Suite.set(0, target), s -> Suite.set(0, s.direct()));
    }

    public static<T, T1 extends T> Fun suppress(Var<T1> source, Var<T> target, BiPredicate<T1, T> suppressor) {
        return new Fun(Suite.set(0, source).set(target), Suite.set(0, target),
                s -> suppressor.test(s.recent().asExpected(), s.asExpected()) ? Suite.set() : s);
    }

    public static<T, T1 extends T> Fun suppressIdentity(Var<T1> source, Var<T> target) {
        return new Fun(Suite.set(0, source).set(target), Suite.set(0, target),
                s -> s.direct() == s.recent().direct() ? Suite.set() : s);
    }

    public static<T, T1 extends T> Fun suppressEquality(Var<T1> source, Var<T> target) {
        return new Fun(Suite.set(0, source).set(target), Suite.set(0, target),
                s -> Objects.equals(s.direct(), s.recent().direct()) ? Suite.set() : s);
    }

    Subject inputs;
    Subject outputs;
    Action transition;
    boolean detection;

    public Fun(Subject inputs, Subject outputs, Action transition) {
        this.inputs = inputs.front().advance(s -> {
            Var<?> v;
            if(s.assigned(Var.class)) {
                v = s.asExpected();
            } else if(s.direct() == SELF) {
                v = new Var<>(this, false, true);
            } else {
                v = new Var<>(s.direct(), false, true);
            }
            v.attachOutput(this);
            return Suite.set(s.key().direct(), v);
        }).toSubject();
        this.outputs = outputs;
        this.transition = transition;
        this.outputs.front().values().filter(Var.class).forEach(v -> v.attachInput(this));
    }

    public void evaluate() {
        Subject inputParams = inputs.front().advance(s -> Suite.set(s.key().direct(), s.asGiven(Var.class).get(this))).toSubject();
        if(detection) {
            detection = false;
            Subject outputParams = transition.play(inputParams);
            outputParams.front().forEach(s -> {
                var output = outputs.get(s.key().direct());
                if (output.settled()) {
                    Var<?> v = output.asExpected();
                    v.set(s.asExpected(), this);
                }
            });
        }
    }

    public boolean press(boolean direct) {
        if(utilized())throw new RuntimeException("Press on utilized Fun");
        if(detection) return false;
        if(direct)detection = true;
        for(var v : outputs.front().values().filter(Var.class)) {
            if(v.press(this))return true;
        }
        return false;
    }

    public void cancel() {
        if(utilized())return;
        Subject collector = Suite.set(this);
        outputs.front().keys().filter(Var.class).forEach(v -> v.collectTransient(collector));
        inputs.front().keys().filter(Var.class).forEach(v -> v.collectTransient(collector));
        Var.utilizeCollected(collector);
    }

    public void detachOutput(Var<?> output) {
        if(utilized())return;
        silentDetachOutput(output);
        Subject collector = Suite.set();
        collectTransient(collector);
        output.collectTransient(collector);
        Var.utilizeCollected(collector);
    }

    public void detachInput(Var<?> input) {
        if(utilized())return;
        for (var s : inputs.front()){
            if(s.direct().equals(input)) {
                cancel();
                return;
            }
        }
    }

    boolean collectTransient(Subject collector) {
        if(utilized())return true;
        if(collector.get(this).settled())return true;
        collector.set(this);
        boolean collect = outputs.front().keys().filter(Var.class).allTrue(f -> f.collectTransient(collector));
        if(collect) inputs.front().keys().filter(Var.class).forEach(f -> f.collectTransient(collector));
        else collector.unset(this);
        return collect;
    }

    void silentDetachOutput(Var<?> output) {
        if(utilized())return;
        for (var s : outputs.front()){
            if(s.direct().equals(output)) {
                outputs.unset(s.key().direct());
            }
        }
    }

    void utilize(Subject collector) {
        if(utilized())return;
        for(Var<?> v : inputs.front().values().filter(Var.class).filter(v -> collector.get(v).desolated())) {
            v.silentDetachOutput(this);
        }
        for(Var<?> v : outputs.front().values().filter(Var.class).filter(v -> collector.get(v).desolated())) {
            v.silentDetachInput(this);
        }
        inputs = null;
        outputs = null;
        transition = null;
    }

    public boolean utilized() {
        return inputs == null;
    }
}
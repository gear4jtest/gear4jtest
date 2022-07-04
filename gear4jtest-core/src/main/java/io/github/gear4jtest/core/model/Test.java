package io.github.gear4jtest.core.model;

public class Test {

    public static void main(String[] args) {
        Chain<String> farm = new Chain<>();
        farm
                .branches()
                    .branch()
                        .step()
                            .operation(new Step1())
                        .done()
//                        .returns("", Integer.class)
                    .done()
                .returns("", Integer.class)
                .done();
    }

    static abstract class Base<U, S, T extends Base<S, S, V, ?>, V extends Base<S, S, ?, ?>> {
        //
        // protected final V parent;
        //
        // Base() {
        // this.parent = null;
        // }
        //
        // Base(V parent) {
        // this.parent = parent;
        // }
        //
        // public V done() throws NullPointerException {
        // if (parent != null) {
        // return parent;
        // }
        //
        // throw new NullPointerException();
        // }
    }

    static class NoParent<U> extends Base<U, U, NoParent<U>, NoParent<U>> {

    }

    static class Chain<U> extends Base<U, U, NoParent<U>, NoParent<U>> {

        private final ChainBranches<U, Chain<U>> branches;

        public Chain() {
            this.branches = new ChainBranches<>(this);
        }

        public ChainBranches<U, Chain<U>> branches() {
            return this.branches;
        }

    }

    static class ChainBranches<U, T extends Base<U, U, NoParent<U>, NoParent<U>>> extends Base<U, U, T, NoParent<U>> {

        private Branch<U, U, ChainBranches<U, T>, T> branch;

        private Chain<U> parent;

        public ChainBranches(Chain<U> parent) {
            this.parent = parent;
            this.branch = new Branch<>(this);
        }

        public Branch<U, U, ChainBranches<U, T>, T> branch() {
            return branch;
        }
        
        public ChainBranches<U, T> returns() {
            return this;
        }

        @SuppressWarnings("unchecked")
        public <A> ChainBranches<A, T> returns(String expression, Class<A> clazz) {
            return (ChainBranches<A, T>) this;
        }

        public Chain<U> done() {
            return parent;
        }

    }

    static class Branch<U, S, T extends Base<S, S, V, ?>, V extends Base<S, S, NoParent<S>, NoParent<S>>> extends Base<U, S, T, V> {

        private Step<U, U, Branch<U, U, T, V>, ?> step;

        private ChainBranches<S, V> parent;

        public Branch(ChainBranches<S, V> parent) {
            init();
        }

        private void init() {
//             this.step = new Step<>(this);
        }
        
        public Step<U, U, Branch<U, U, T, V>, ?> step() {
            return step;
        }

        @SuppressWarnings("unchecked")
        public <A> Branch<A, S, T, V> returns(String expression, Class<A> clazz) {
            return (Branch<A, S, T, V>) this;
        }

        public ChainBranches<S, V> done() {
            return parent;
        }

    }

    static class Step<U, S, T extends Base<S, S, V, ?>, V extends Base<S, S, NoParent<S>, NoParent<S>>> extends Base<U, S, T, V> {

        private Branch<U, S, V, ?> parent;

        public Step(Branch<U, S, V, ?> parent) {
            init();
        }

        private void init() {
            // this.step = new Step<>(this);
        }

        @SuppressWarnings("unchecked")
        public <OUT> Step<OUT, S, T, V> operation(Stepa<U, OUT> operation) {
            return (Step<OUT, S, T, V>) this;
        }

        @SuppressWarnings("unchecked")
        public <A> Step<A, S, T, V> returns(String expression, Class<A> clazz) {
            return (Step<A, S, T, V>) this;
        }

        public Branch<U, S, V, ?> done() {
            return parent;
        }

    }
    
    public interface Stepa<IN, OUT> {

        OUT execute(IN object);
        
    }
    
    static class Step1 implements Stepa<String, Integer> {

        @Override
        public Integer execute(String object) {
            return null;
        }
        
    }
	
}

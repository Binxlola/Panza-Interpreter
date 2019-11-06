package com.panzainterpreter.panza;

import java.util.List;

public class PanzaFunction implements PanzaCallable {
    private final  Stmt.Function declaration;
    private final Environment closure;
    private final boolean isInitializer;

    PanzaFunction(Stmt.Function declaration, Environment closure, boolean isInitializer) {
        this.declaration = declaration;
        this.closure = closure;
        this.isInitializer = isInitializer;

    }

    /**
     * Create a environment nested inside the methods original closure. Then declares "this" as a variable in that
     * environment and bind it to the given instance.
     * @param instance
     * @return
     */
    PanzaFunction bind(PanzaInstance instance) {
        Environment environment = new Environment(closure);
        environment.define("this", instance);
        return new PanzaFunction(declaration, environment, isInitializer);
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Environment environment = new Environment(closure);
        for (int i = 0; i < declaration.params.size(); i++) {
            environment.define(declaration.params.get(i).lexeme, arguments.get(i));
        }

        try {
            interpreter.executeBlock(declaration.body, environment);
        } catch (Return returnValue) { // Catches a return exception causing stack to unwind
            if (isInitializer) return closure.getAt(0, "this");

            return returnValue.value;
        }

        if (isInitializer) return closure.getAt(0, "this");

        return null;
    }

    @Override
    public int arity() { return declaration.params.size(); }

    @Override
    public String toString() { return "<function " + declaration.name.lexeme + ">"; }
}

package com.panzainterpreter.panza;

import java.util.List;
import java.util.Map;

public class PanzaClass implements PanzaCallable {
    final String name;
    final PanzaClass superclass;
    private final Map<String, PanzaFunction> methods;

    PanzaClass(String name, PanzaClass superclass, Map<String, PanzaFunction> methods) {
        this.name = name;
        this.superclass = superclass;
        this.methods = methods;
    }

    PanzaFunction findMethod(String name) {
        if (methods.containsKey(name)) {
            return methods.get(name);
        }

        if (superclass != null) {
            return superclass.findMethod(name);
        }

        return null;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        PanzaInstance instance = new PanzaInstance(this);
        PanzaFunction initializer = findMethod("init");
        if (initializer != null) {
            initializer.bind(instance).call(interpreter, arguments);
        }
        return instance;
    }

    @Override
    public int arity() {
        PanzaFunction initializer = findMethod("init");
        if (initializer == null) return 0;
        return initializer.arity();
    }
}

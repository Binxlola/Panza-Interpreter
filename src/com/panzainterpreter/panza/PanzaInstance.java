package com.panzainterpreter.panza;

import java.util.HashMap;
import java.util.Map;

public class PanzaInstance {
    private PanzaClass klass;
    private final Map<String, Object> fields = new HashMap<>();

    PanzaInstance(PanzaClass klass) {
        this.klass = klass;
    }

    /**
     * Check to see if the instance has a specific field, if it does return it. Otherwise throw a runtime error.
     * @param name
     * @return
     */
    Object get(Token name) {
        if (fields.containsKey(name.lexeme)) {
            return fields.get(name.lexeme);
        }

        PanzaFunction method = klass.findMethod(name.lexeme);
        if (method != null) return method.bind(this);

        throw new RuntimeError(name, "Undefined property '" + name.lexeme + "'.");
    }

    /**
     * Adds the new field to the objects instance.
     * @param name
     * @param value
     */
    void set(Token name, Object value) {
        fields.put(name.lexeme, value);
    }

    @Override
    public String toString() {
        return klass.name + "instance";
    }
}

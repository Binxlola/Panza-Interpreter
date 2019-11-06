package com.craftinginterpreters.panza;

import java.util.HashMap;
import java.util.Map;

public class Environment {
    final Environment enclosing;
    private final Map<String, Object> values = new HashMap<>();

    Environment() {
        enclosing = null;
    }

    Environment (Environment enclosing) {
        this.enclosing = enclosing;
    }

    /**
     * Checks the hash map for the queried variable, if the variable has been defined it's value will be returned.
     * If the variable has not been define a RuntimeError will be thrown.
     * @param name The name of the variable being queried
     * @return The value associated with the queried variable
     */
    Object get(Token name) {
        if (values.containsKey(name.lexeme)) {
            return values.get(name.lexeme);
        }

        if (enclosing != null) return enclosing.get(name);

        throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
    }

    void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
            return;
        }

        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }

        throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
    }

    void define(String name, Object value) {
        values.put(name, value);
    }

    /**
     * Walks a fixed number of hops up the parent chain and returns the environment there.
     * @param distance
     * @return
     */
    private Environment ancestor(int distance) {
        Environment environment = this;
        for (int i = 0; i < distance; i++) {
            environment = environment.enclosing;
        }

        return environment;
    }

    Object getAt(int distance, String name) {
        return ancestor(distance).values.get(name);
    }

    void assignAt(int distance, Token name, Object value) {
        ancestor(distance).values.put(name.lexeme, value);
    }
}

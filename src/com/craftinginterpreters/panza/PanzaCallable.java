package com.craftinginterpreters.panza;

import java.util.List;

public interface PanzaCallable {
    Object call(Interpreter interpreter, List<Object> arguments);
    int arity();
}

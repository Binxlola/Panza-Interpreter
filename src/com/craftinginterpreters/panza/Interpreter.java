package com.craftinginterpreters.panza;

import java.util.*;

public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    final Environment globals = new Environment();
    private Environment environment = globals;
    private final Map<Expr, Integer> locals = new HashMap<>();

    /**
     * Defines native functions
     */
    Interpreter() {
        globals.define("clock", new PanzaCallable() {
            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double)System.currentTimeMillis() / 1000.0;
            }

            @Override
            public int arity() {
                return 0;
            }

            @Override
            public String toString() { return "<native function>"; }
        });
    }

    void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement: statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Panza.runtimeError(error);
        }
    }

    /**
     * It takes in a syntax tree for an expression and evaluates it.
     * If that succeeds, evaluate() returns an object for the result value.
     * interpret() converts that to a string and shows it to the user.
     * @param object
     * @return
     */
    private String stringify(Object object) {
        if (object == null) return "nil";

        // Hack. Work around Java adding ".0" to integer-valued doubles.
        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() -2);
            }
            return text;
        }

        return object.toString();
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left)) return left;
        } else {
            if (!isTruthy(left)) return left;
        }

        return evaluate(expr.right);
    }

    /**
     * Evaluates the object whose property is being set, and check if its a LuxInstance. If not it's a runtime error.
     * @param expr
     * @return
     */
    @Override
    public Object visitSetExpr(Expr.Set expr) {
        Object object = evaluate(expr.object);

        if (!(object instanceof PanzaInstance)) {
            throw new RuntimeError(expr.name, "Only instance have fields");
        }

        Object value = evaluate(expr.value);
        ((PanzaInstance)object).set(expr.name, value);
        return value;
    }

    @Override
    public Object visitSuperExpr(Expr.Super expr) {
        // Look up the superclass by finding 'super' in the proper environment.
        int distance = locals.get(expr);
        PanzaClass superclass = (PanzaClass)environment.getAt(distance, "super");

        // "this" is always one level nearer than "super"'s environment.
        PanzaInstance object = (PanzaInstance)environment.getAt(distance - 1, "this");

        PanzaFunction method = superclass.findMethod(expr.method.lexeme);

        // Throw a runtime error if the method does not exist in the superclass
        if (method == null) {
            throw new RuntimeError(expr.method, "Undefined property '" + expr.method.lexeme + "'.");
        }
        return method.bind(object);
    }

    @Override
    public Object visitThisExpr(Expr.This expr) {
        return lookUpVariable(expr.keyword, expr);
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case BANG:
                return !isTruthy(right);
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double)right;
        }

        //unreachable
        return null;
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return lookUpVariable(expr.name, expr);
    }

    /**
     * First looks up the resolved distance in the map. If the variable is not in the locals map then it must be a global.
     * If this is the case, we look it up dynamically, directly from the global environment. If there was a distance
     * then we have a local variable and we retrieve it from our locals environment.
     * @param name
     * @param expr
     * @return
     */
    private Object lookUpVariable(Token name, Expr expr) {
        Integer distance = locals.get(expr);
        if (distance != null) {
            return environment.getAt(distance, name.lexeme);
        } else {
            return globals.get(name);
        }
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body);
        }
        return null;
    }

    private boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean)object;
        return true;
    }

    private Boolean isEqual(Object a, Object b) {
        // nil is only equal to nil
        if (a == null && b == null) return true;
        if (a == null) return false;

        return a.equals(b);
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    /**
     * Stores resolution data for a given variable in the locals hashMap
     * @param expr
     * @param depth
     */
    void resolve(Expr expr, int depth) {
        locals.put(expr, depth);
    }

    /**
     * Will execute a list of statements in the context of the given environment. The innermost environment that represents
     * the scope in which the code is being executed.
     * @param statements
     * @param environment
     */
    void executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;
        try {
            this.environment = environment;

            for (Stmt statement: statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    /**
     * Declare the class name in the current environment. Turn class node syntax into a LuxClass
     * @param stmt
     * @return
     */
    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        // Deal with a super class
        Object superclass = null;
        if (stmt.superclass != null) {
            superclass = evaluate(stmt.superclass);
            if (!(superclass instanceof PanzaClass)) {
                throw new RuntimeError(stmt.superclass.name, "Superclass must be a class");
            }
        }

        // Define the class
        environment.define(stmt.name.lexeme, null);

        // Create an environment in the enviroment chain the holds a reference to the superclass
        if (stmt.superclass != null) {
            environment = new Environment(environment);
            environment.define("super", superclass);
        }
        Map<String, PanzaFunction> methods = new HashMap<>();
        for (Stmt.Function method : stmt.methods) {
            PanzaFunction function = new PanzaFunction(method, environment, method.name.lexeme.equals("init"));
            methods.put(method.name.lexeme, function);
        }

        PanzaClass klass = new PanzaClass(stmt.name.lexeme, (PanzaClass)superclass, methods);

        if (superclass != null) {
            environment = environment.enclosing;
        }
        environment.assign(stmt.name, klass);
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        PanzaFunction function = new PanzaFunction(stmt, environment, false);
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if (stmt.value != null) value = evaluate(stmt.value);

        throw new Return(value);
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = null;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }

        environment.define(stmt.name.lexeme, value);
        return null;
    }

    /**
     * Looks up the variables scope distance. If not found, it's assumed to be global.
     * @param expr
     * @return
     */
    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);

        Integer distance = locals.get(expr);
        if (distance != null) {
            environment.assignAt(distance, expr.name, value);
        } else {
            globals.assign(expr.name, value);
        }
        return value;
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double)left > (double)right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left >= (double)right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left < (double)right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left <= (double)right;
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left - (double)right;
            case PLUS:
                if (left instanceof Double && right instanceof Double) {
                    return (double)left + (double)right;
                }

                if (left instanceof String && right instanceof String) {
                    return (String)left + (String)right;
                }

                throw new RuntimeError(expr.operator,
                        "Operands must be two numbers or two strings.");
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                return (double)left / (double)right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return  (double)left * (double)right;
            case BANG_EQUAL: return !isEqual(left, right);
            case EQUAL_EQUAL: return isEqual(left, right);
        }

        //unreachable
        return null;
    }

    /**
     * First, we evaluate the expression for the callee. Typically, this expression is just an identifier that looks up
     * the function by its name, but it could be anything. Then we evaluate each of the argument expressions in order and
     * store the resulting values in a list.
     *
     * This is another one of those subtle semantic choices. Since argument expressions may have side effects, the order
     * they are evaluated could be user visible. Even so, some languages like Scheme and C don’t specify an order.
     * This gives compilers freedom to reorder them for efficiency, but means users may be unpleasantly surprised if
     * arguments aren’t evaluated in the order they expect.
     *
     * Once we’ve got the callee and the arguments ready, all that remains is to perform the call. We do that by casting
     * the callee to a LoxCallable and then invoking a call() method on it. The Java representation of any Lox object
     * that can be called like a function will implement this interface. That includes user-defined functions, naturally,
     * but also class objects since classes are “called” to construct new instances. We’ll also use it for one more
     * purpose shortly.
     * @param expr
     * @return
     */
    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee);

        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument));
        }

        // Check that a function or class is being called
        if (!(callee instanceof PanzaCallable)) {
            throw new RuntimeError(expr.paren, "Can only call functions and classes.");
        }

        PanzaCallable function = (PanzaCallable)callee;
        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren, "Expected " + function.arity() + " arguments but got " + arguments.size() + ".");
        }
        return function.call(this, arguments);
    }

    @Override
    public Object visitGetExpr(Expr.Get expr) {
        Object object = evaluate(expr.object);
        if (object instanceof PanzaInstance) {
            return ((PanzaInstance)object).get(expr.name);
        }

        throw new RuntimeError(expr.name, "Only instances have properties.");
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number");
    }

    private void checkNumberOperands(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double) return;
        throw new RuntimeError(operator, "Operands must be numbers");
    }
}

package com.panzainterpreter.panza;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.panzainterpreter.panza.TokenType.*;

public class Parser {

    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /**
     * Used to start the parser.
     * @return
     */
    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }

        return statements;
    }

    private Expr expression() {
        return assignment();
    }

    /**
     * Checks to see if we are at a variable declaration by looking for the leading 'var' keyword.
     * If not it will bubble up to the higher precedence statement method.
     * @return
     */
    private Stmt declaration() {
        try {
            if (match(CLASS)) return classDeclaration();
            if (match(FUNCTION)) return function("function");
            if (match(VARIABLE)) return varDeclaration();

            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt classDeclaration() {
        Token name = consume(IDENTIFIER, "Expect class name.");

        Expr.Variable superclass = null;
        if (match(LESS)) {
            consume(IDENTIFIER, "Expect superclass name.");
            superclass = new Expr.Variable(previous());
        }
        consume(LEFT_BRACE, "Expect '{' before class body.");

        List<Stmt.Function> methods = new ArrayList<>();
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            methods.add(function("method"));
        }

        consume(RIGHT_BRACE, "Expect '}' after class body");

        return new Stmt.Class(name, superclass, methods);
    }

    private Stmt statement() {
        if (match(FOR)) return forStatement();
        if (match(IF)) return ifStatement();
        if (match(PRINT)) return printStatement();
        if (match(RETURN)) return returnStatement();

        if (match(LEFT_BRACE)) return new Stmt.Block(block());

        return expressionStatement();
    }

    /**
     * If the token following the ( is a semicolon then the initializer has been omitted. Otherwise,
     * we check for a var keyword to see if it’s a variable declaration. If neither of those matched,
     * it must be an expression. We parse that and wrap it in an expression statement so that the initializer is
     * always of type Stmt.
     * @return
     */
    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'for'.");

        Stmt initializer;
        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VARIABLE)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition");

        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses.");

        Stmt body = statement();

        //The increment, if there is one, executes after the body in each iteration of the loop. We do that by replacing
        // the body with a little block that contains the original body followed by an expression statement that evaluates the increment.
        if (increment != null) {
            body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(increment)));
        }

        //Next, we take the condition and the body and build the loop using a primitive while loop. If the condition
        // is omitted, we jam in true to make an infinite loop.
        if (condition == null) condition = new Expr.Literal(true);
        body = new Stmt.While(condition, body);

        // Finally, if there is an initializer, it runs once before the entire loop. We do that by, again, replacing the
        // whole statement with a block that runs the initializer and then executes the loop.
        if (initializer != null) {
            body = new Stmt.Block(Arrays.asList(initializer, body));
        }
        return body;
    }

    /**
     * Called when an if statement is hit, will parse and execute the containing statements/expressions. If a else
     * statement is found after this will be executed, if not the else branch will stay null.
     * @return
     */
    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after if condition.");

        Stmt thenBranch = statement();
        Stmt elseBranch= null;
        if (match(ELSE)) {
            elseBranch = statement();
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    /**
     * Called after the a print token has been found, will then consume the subsequent expression upto the
     * terminating semicolon.
     * @return The syntax tree
     */
    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private Stmt returnStatement() {
        Token keyword = previous();
        Expr value = null;
        if (!check(SEMICOLON)) {
            value = expression();
        }

        consume(SEMICOLON, "Expect ';' after return value");
        return new Stmt.Return(keyword, value);
    }

    /**
     * When the parser matches a VARIABLE token it will branch to this method, the recursive decent code follows the
     * grammer rules. The var token has been matched so next it will require and consumes the identifier token for the
     * variable name. Then when it sees '=' token it knows there is an initializer expression and will parse it.
     * Otherwise it will leave the initializer null. Finally we will consume the required semicolon at the end of the statement.
     * @return
     */
    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }

        consume(SEMICOLON, "Expect ';' after the variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    /**
     * If no other statement type is found, we assume it is an expression statement and this method is called.
     * Will consume token up to the terminating semicolon.
     * @return The syntax tree
     */
    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Expression(expr);
    }

    private Stmt.Function function(String kind) {
        Token name = consume(IDENTIFIER, "Expect " + kind + " name.");
        List<Token> parameters = new ArrayList<>();

        consume(LEFT_PAREN, "Expect '( after " + kind + " name.");
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size() >= 255) {
                    error(peek(), "Cannot have more than 255 parameters.");
                }

                parameters.add(consume(IDENTIFIER, "Expect parameter name."));
            } while (match(COMMA));
        }

        consume(RIGHT_PAREN, "Expect ')' after parameters.");

        consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
        List<Stmt> body = block();
        return new Stmt.Function(name, parameters, body);
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    private Expr assignment() {
        Expr expr = or();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            } else if (expr instanceof Expr.Get) {
                Expr.Get get = (Expr.Get)expr;
                return new Expr.Set(get.object, get.name, value);
            }
            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    private Expr or() {
        Expr expr = and();

        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        Expr expr = equality();

        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    // Is binary can be unified
    private Expr equality() {
        Expr expr = comparison();

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // Is binary can be unified
    private Expr comparison() {
        Expr expr = addition();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = addition();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // Is binary can be unified
    private Expr addition() {
        Expr expr = multiplication();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = multiplication();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // Is binary can be unified
    private Expr multiplication() {
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return call();
    }

    /**
     * This is more or less the arguments grammar rule translated to code, except that we also handle the zero-argument
     * case. We check for that case first by seeing if the next token is ). If it is, we don’t try to parse any arguments.
     *
     * Otherwise, we parse an expression, then look for a comma indicating that there is another argument after that.
     * We keep doing that as long as we find commas after each expression. When we don’t find a comma, then the argument
     * list must be done and we consume the expected closing parenthesis. Finally, we wrap the callee and those arguments
     * up into a call expression.
     * @param callee
     * @return
     */
    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                arguments.add(expression());
                if (arguments.size() >= 255) {
                    error(peek(), "Cannot have more than 255 arguments");
                }
            } while (match(COMMA));
        }

        Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");

        return new Expr.Call(callee, paren, arguments);
    }

    /**
     * First, we parse a primary expression, the “left operand” to the call. Then, each time we see a
     * (, we call finishCall() to parse the call expression using the previously parsed expression as the callee.
     * The returned expression becomes the new expr and we loop to see if the result is itself called.
     * @return
     */
    private Expr call() {
        Expr expr = primary();

        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr);
            } else if (match(DOT)) {
                Token name = consume(IDENTIFIER, "Expect property name after '.'.");
                expr = new Expr.Get(expr, name);
            } else {
                break;
            }
        }

        return expr;
    }

    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(SUPER)) {
            Token keyword = previous();
            consume(DOT, "Expect '.' after 'super'.");
            Token method = consume(IDENTIFIER, "Expect superclass method name");
            return new Expr.Super(keyword, method);
        }

        if (match(THIS)) return new Expr.This(previous());

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression");
    }

    /**
     * This method checks to see if the current token is any of the given types. If so, it consumes the token and
     * return true. Otherwise it returns false and leave the token as the current one.
     * @param types
     * @return
     */
    private boolean match(TokenType... types) { // ... means zero or more parameters of the given type.
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    /**
     * This method checks to see if the next token is of the expected type. If so it consumes it.
     * If not then we have hit and error and it will be reported.
     * @param type
     * @param message
     * @return
     */
    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();

        throw error(peek(), message);
    }

    /**
     * This method looks at the current token and returns true if it is of the given type, note this does not consume
     * the token, it only looks at it.
     * @param type
     * @return
     */
    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    /**
     * This method will consume the current token and return it.
     * @return
     */
    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    /**
     * This method calls the peek() method to see if we are at the end of the file.
     * @return
     */
    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    /**
     * This method looks ahead for the next token we have yet to consume in the file.
     * @return
     */
    private  Token peek() {
        return tokens.get(current);
    }

    /**
     * This method looks back for the previous consumed token in the file.
     * @return
     */
    private Token previous() {
        return tokens.get(current - 1);
    }

    /**
     * This method displays an error to the user by calling the main error() method in Lux.java.
     * returns an instance of ParseError instead of throwing because we want to let the caller decide whether to unwind or not.
     * @param token
     * @param message
     * @return
     */
    private ParseError error(Token token, String message) {
        Panza.error(token, message);
        return new ParseError();
    }

    /**
     * Used when an error has occurred while parsing. Will move post the code that presented an error and look for the
     * start of the next statement so the parser can continue to parse code in search of any further errors. Tokens that
     * do not imply a statement boundary will be thrown away.
     */
    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) return;

            switch (peek().type) {
                case CLASS:
                case FUNCTION:
                case VARIABLE:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }
}

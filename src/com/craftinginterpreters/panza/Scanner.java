package com.craftinginterpreters.panza;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.craftinginterpreters.panza.TokenType.*;

public class Scanner {

    private final String source;
    private final List<Token> tokens = new ArrayList<>();

    // For the parsing loop
    private int start = 0;
    private int current = 0;
    private int line = 1;

    // Hash map to hold all reserved words for the lux language
    private static final Map<String, TokenType> keywords;

    static  {
        keywords = new HashMap<>();
        keywords.put("and",    AND);
        keywords.put("class",  CLASS);
        keywords.put("else",   ELSE);
        keywords.put("false",  FALSE);
        keywords.put("for",    FOR);
        keywords.put("fun",    FUNCTION);
        keywords.put("if",     IF);
        keywords.put("nil",    NIL);
        keywords.put("or",     OR);
        keywords.put("print",  PRINT);
        keywords.put("return", RETURN);
        keywords.put("super",  SUPER);
        keywords.put("this",   THIS);
        keywords.put("true",   TRUE);
        keywords.put("var",    VARIABLE);
        keywords.put("while",  WHILE);
    }

    Scanner(String source) {
        this.source = source;
    }

    List<Token> scanTokens() {
        while (!isAtEnd()) {
            // This is the beginning of the next lexeme.
            start = current;
            scanToken();
        }

        tokens.add(new Token(EOF, "", null, line));
        return tokens;
    }

    private void scanToken() {
        char c = advance();
        switch(c) {
            // Single-character lexemes.
            case '(': addToken(LEFT_PAREN); break;
            case ')': addToken(RIGHT_PAREN); break;
            case '{': addToken(LEFT_BRACE); break;
            case '}': addToken(RIGHT_BRACE); break;
            case ',': addToken(COMMA); break;
            case '.': addToken(DOT); break;
            case '-': addToken(MINUS); break;
            case '+': addToken(PLUS); break;
            case ';': addToken(SEMICOLON); break;
            case '*': addToken(STAR); break;

            // Single/pair lexemes.
            case '!': addToken(match('=') ? BANG_EQUAL : BANG); break;
            case '=': addToken(match('=') ? EQUAL_EQUAL : EQUAL); break;
            case '<': addToken(match('=') ? LESS_EQUAL : LESS); break;
            case '>': addToken(match('=') ? GREATER_EQUAL : GREATER); break;

            // Special case lexemes.
            case '/':
                if (match('/')) {
                    // A comment goes until the end of the line.
                    while (peek() != '\n' && !isAtEnd()) advance();
                } else {
                    addToken(SLASH);
                }
                break;

            // Ignored lexemes where no token is created.
            case ' ':
            case '\r':
            case '\t':
                // Ignore whitespace.
                break;

            case '\n':
                line++;
                break;

            // String lexemes.
            case '"': string(); break;

            // Account for possible characters that are not supported by the language, the character will still be
            // consumed by the advance() method.
            // Also accounts for number literals
            // Also accounts for a identifier, essentially anything that starts with an alpha character that is not covered above.
            default:
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    Panza.error(line, "Unexpected character.");
                }
                break;
        }
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) advance();

        // Check to see if the identifier is a reserved word
        String text = source.substring(start, current);

        TokenType type = keywords.get(text);
        if (type == null) type = IDENTIFIER;

        addToken(type);
    }

    /**
     * This method takes a number lexeme and creates a numeric token from it's value.
     */
    private void number() {
        // Consumes the first set of digits before the '.'
        while (isDigit(peek())) advance();

        // Here we look for any fractional parts
        if (peek() == '.' && isDigit(peekNext())) {
            // Consume the '.'
            advance();

            // Continues to consume the digits are the '.'
            while (isDigit(peek())) advance();
        }

        // Converts the string representation of the number to a numeric value
        addToken(NUMBER, Double.parseDouble((source.substring(start, current))));
    }

    private void string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++;
            advance();
        }

        // Unterminated string.
        if (isAtEnd()) {
            Panza.error(line, "Unterminated string.");
            return;
        }

        // The closing ".
        advance();

        // Trim the surrounding quotes.
        String value = source.substring(start + 1, current - 1);
        addToken(STRING, value);
    }

    /**
     * A method that checks if the next character after a single-character lexeme is part of the current lexeme,
     * making it a double-character lexeme.
     * @param expected A string with the character expected to make the current lexeme a double-character lexeme.
     * @return A boolean value if the next character is what was expected.
     */
    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;

        current++;
        return true;
    }

    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                (c == '_');
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    /**
     * This method checks to see if a given character is a number character (digit).
     * @param c The character being checked.
     * @return A boolean value describing if the character is in fact a number.
     */
    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /**
     * This method is used to check if our scanner has reached the end of the source code.
     * @return A boolean value describing if the current value is equal to the source length.
     */
    private boolean isAtEnd() {
        return current >= source.length();
    }

    /**
     * Consumes the next character in the source file and returns it.
     * @return The char in the source file.
     */
    private char advance() {
        current++;
        return source.charAt(current - 1);
    }

    /**
     * Creates a new token from the current lexeme.
     * @param type The type of token to be created.
     */
    private void addToken(TokenType type) {
        addToken(type, null);
    }

    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }
}

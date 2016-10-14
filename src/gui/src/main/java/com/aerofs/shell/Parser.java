package com.aerofs.shell;

import com.aerofs.base.ex.ExBadArgs;

import java.util.ArrayList;

public class Parser {

    static enum State {
        START,
        INPUT,
        INPUT_ESC,
        QUOTE,
        QUOTE_ESC,
        ERROR,
    }

    static enum Input {
        SPACE,
        LETTER,
        ESCAPE,
        QUOTE,
    }

    State onStart(char ch, Input input)
    {
        switch (input) {
        case LETTER:
            _sb = new StringBuilder();
            _sb.append(ch);
            return State.INPUT;
        case SPACE:
            return State.START;
        case QUOTE:
            _sb = new StringBuilder();
            return State.QUOTE;
        case ESCAPE:
            _sb = new StringBuilder();
            return State.INPUT_ESC;
        default:
            throw new IllegalStateException();
        }
    }

    State onInput(char ch, Input input)
    {
        switch (input) {
        case LETTER:
            _sb.append(ch);
            return State.INPUT;
        case SPACE:
            _args.add(_sb.toString());
            _sb = null;  // for debugging only
            return State.START;
        case QUOTE:
            return State.QUOTE;
        case ESCAPE:
            return State.INPUT_ESC;
        default:
            throw new IllegalStateException();
        }
    }

    State onInputEsc(char ch, Input input)
    {
        switch (input) {
        case LETTER:
            _err = "invalid escape char: \'" + ch + "\'";
            return State.ERROR;
        case SPACE:
        case QUOTE:
        case ESCAPE:
            _sb.append(ch);
            return State.INPUT;
        default:
            throw new IllegalStateException();
        }
    }

    State onQuote(char ch, Input input)
    {
        switch (input) {
        case LETTER:
        case SPACE:
            _sb.append(ch);
            return State.QUOTE;
        case QUOTE:
            return State.INPUT;
        case ESCAPE:
            return State.QUOTE_ESC;
        default:
            throw new IllegalStateException();
        }
    }

    State onQuoteEsc(char ch, Input input)
    {
        switch (input) {
        case LETTER:
            _err = "invalid escape char: \'" + ch + "\'";
            return State.ERROR;
        case SPACE:
        case QUOTE:
        case ESCAPE:
            _sb.append(ch);
            return State.QUOTE;
        default:
            throw new IllegalStateException();
        }
    }

    Input getInput(char ch)
    {
        if (ch == ' ') return Input.SPACE;
        if (ch == '\\') return Input.ESCAPE;
        if (ch == '"') return Input.QUOTE;
        else return Input.LETTER;
    }

    private final ArrayList<String> _args = new ArrayList<String>();
    private StringBuilder _sb;
    private String _err;    // must be set whenever transit to State.ERROR

    @SuppressWarnings("fallthrough")
    String[] parse(String str) throws ExBadArgs
    {
        State state = State.START;

        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            Input input = getInput(ch);
            switch (state) {
            case START:
                state = onStart(ch, input);
                break;
            case INPUT:
                state = onInput(ch, input);
                break;
            case INPUT_ESC:
                state = onInputEsc(ch, input);
                break;
            case QUOTE:
                state = onQuote(ch, input);
                break;
            case QUOTE_ESC:
                state = onQuoteEsc(ch, input);
                break;
            default:
                assert false;
            }

            if (state == State.ERROR) throw new ExBadArgs(_err);
        }

        switch (state) {
        case INPUT:
            _args.add(_sb.toString());
        case START:
            String[] ret = new String[_args.size()];
            return _args.toArray(ret);
        default:
            throw new ExBadArgs("incomplete line");
        }
    }
}

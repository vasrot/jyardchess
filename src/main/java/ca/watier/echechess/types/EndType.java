package ca.watier.echechess.types;

public enum EndType {
    STALEMATE("King under stalemate"),
    W_CHECKMATE("White king under checkmate"),
    B_CHECKMATE("Black king under checkmate"),
    REPETITIVE_MOVES("Same movements 3 times"),
    MATERIAL_DRAW("Not enough material for checkmate"),
    NO_END("Game is not ended yet");

    private final String cause;

    EndType(String cause) {
        this.cause = cause;
    }

    public String getCause(){
        return cause;
    }
}

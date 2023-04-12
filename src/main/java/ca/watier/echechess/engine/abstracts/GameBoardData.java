package ca.watier.echechess.engine.abstracts;

import ca.watier.echechess.common.enums.CasePosition;
import ca.watier.echechess.common.enums.Pieces;
import ca.watier.echechess.common.enums.Side;
import ca.watier.echechess.common.pojos.MoveHistory;
import ca.watier.echechess.engine.utils.GameUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

import static ca.watier.echechess.common.enums.Side.WHITE;

public class GameBoardData implements Cloneable, Serializable {

    @Serial
    private static final long serialVersionUID = -5242416504518941779L;

    //The default position of the board
    private final Map<CasePosition, Pieces> defaultPositions;
    //The pieces position on the board
    private Map<CasePosition, Pieces> positionPiecesMap;
    //Used to check if the piece have moved
    private Map<CasePosition, Boolean> isPiecesMovedMap;
    //Used to check if the pawn used it's special ability to move by two case
    private Map<CasePosition, Boolean> isPawnUsedSpecialMoveMap;
    //Used to track the turn that the piece have moved
    private Map<CasePosition, Integer> turnNumberPieceMap;
    //Used to track the pawn promotions
    private MultiValuedMap<Side, Pair<CasePosition, CasePosition>> pawnPromotionMap;
    //Used to track the number of turn of each player
    private int blackTurnNumber;
    private int whiteTurnNumber;
    private int totalMove;
    private short blackPlayerPoint;
    private short whitePlayerPoint;
    private boolean isGameDraw;
    private boolean isGamePaused;
    private boolean isWhiteQueenCastlingAvailable;
    private boolean isWhiteKingCastlingAvailable;
    private boolean isBlackQueenCastlingAvailable;
    private boolean isBlackKingCastlingAvailable;
    private List<MoveHistory> moveHistoryList;
    private Side currentAllowedMoveSide;
    private boolean allowOtherToJoin;
    private boolean allowObservers;

    public GameBoardData() {
        pawnPromotionMap = new ArrayListValuedHashMap<>(); //FIXME: Note that ArrayListValuedHashMap is not synchronized and is not thread-safe
        defaultPositions = GameUtils.getDefaultGame();
        positionPiecesMap = GameUtils.getDefaultGame();
        isPiecesMovedMap = GameUtils.initNewMovedPieceMap(positionPiecesMap);
        isPawnUsedSpecialMoveMap = GameUtils.initPawnMap(positionPiecesMap);
        turnNumberPieceMap = GameUtils.initTurnMap(positionPiecesMap);
        moveHistoryList = new ArrayList<>();
        blackPlayerPoint = 0;
        whitePlayerPoint = 0;
        currentAllowedMoveSide = WHITE;
        totalMove = 0;
        isGameDraw = false;
        isGamePaused = false;
        isWhiteQueenCastlingAvailable = true;
        isWhiteKingCastlingAvailable = true;
        isBlackQueenCastlingAvailable = true;
        isBlackKingCastlingAvailable = true;
        allowOtherToJoin = false;
        allowObservers = false;
    }

    protected Collection<Pair<CasePosition, CasePosition>> getPawnPromotionBySide(Side playerSide) {
        if (playerSide == null) {
            return new ArrayList<>();
        }

        return CollectionUtils.emptyIfNull(pawnPromotionMap.get(playerSide));
    }

    protected Pieces getPieceFromPosition(CasePosition position) {
        if (position == null) {
            return null;
        }

        return positionPiecesMap.get(position);
    }

    public final Map<CasePosition, Pieces> getPiecesLocation() {
        return Map.copyOf(positionPiecesMap);
    }


    /**
     * Gets the pieces / CasePosition based on a side
     *
     * @param side
     * @return
     */
    public final Map<CasePosition, Pieces> getPiecesLocation(Side side) {
        Map<CasePosition, Pieces> values = new EnumMap<>(CasePosition.class);

        if (side == null) {
            return values;
        }

        for (Map.Entry<CasePosition, Pieces> casePositionPiecesEntry : positionPiecesMap.entrySet()) {
            CasePosition key = casePositionPiecesEntry.getKey();
            Pieces value = casePositionPiecesEntry.getValue();

            if (side.equals(value.getSide())) {
                values.put(key, value);
            }
        }

        return values;
    }

    public Map<CasePosition, Pieces> getDefaultPositions() {
        return Map.copyOf(defaultPositions);
    }

    public int getBlackTurnNumber() {
        return blackTurnNumber;
    }

    public int getWhiteTurnNumber() {
        return whiteTurnNumber;
    }

    public int getNbTotalMove() {
        return totalMove;
    }


    public Map<CasePosition, Boolean> getIsPiecesMovedMap() {
        return Map.copyOf(isPiecesMovedMap);
    }

    public Map<CasePosition, Boolean> getIsPawnUsedSpecialMoveMap() {
        return Map.copyOf(isPawnUsedSpecialMoveMap);
    }

    public Map<CasePosition, Integer> getTurnNumberPieceMap() {
        return Map.copyOf(turnNumberPieceMap);
    }

    /**
     * Get the turn number based on a {@link CasePosition}
     *
     * @param position
     * @return
     */
    public final Integer getPieceTurn(CasePosition position) {
        if (position == null) {
            return null;
        }

        return turnNumberPieceMap.get(position);
    }

    protected void addPawnPromotionToMap(Side side, Pair<CasePosition, CasePosition> casePositionCasePositionPair) {
        if (ObjectUtils.anyNull(side, casePositionCasePositionPair)) {
            return;
        }

        pawnPromotionMap.put(side, casePositionCasePositionPair);
    }

    public final void removePiece(CasePosition from) {
        if (from == null) {
            return;
        }

        positionPiecesMap.remove(from);
    }

    public void setPiecePositionWithoutMoveState(Pieces piece, CasePosition to) {
        if (ObjectUtils.anyNull(piece, to)) {
            return;
        }

        positionPiecesMap.put(to, piece);
    }

    /**
     * If it's the default from of the piece, mark this one as moved
     *
     * @param piece
     * @param from
     * @param to
     */
    protected void changeMovedStateOfPiece(Pieces piece, CasePosition from, CasePosition to) {
        if (ObjectUtils.anyNull(piece, from, to)) {
            return;
        }

        isPiecesMovedMap.put(to, true);
        isPiecesMovedMap.remove(from);
    }

    /**
     * Check if the piece is moved, return null if the position is invalid
     *
     * @param position
     * @return
     */
    public final boolean isPieceMoved(CasePosition position) {
        if (position == null) {
            return false;
        }

        return BooleanUtils.toBoolean(isPiecesMovedMap.get(position));
    }


    /**
     * Return true if the pawn used the special move
     *
     * @param position
     * @return
     */
    public final boolean isPawnUsedSpecialMove(CasePosition position) {
        if (position == null) {
            return false;
        }

        return BooleanUtils.toBoolean(isPawnUsedSpecialMoveMap.get(position));
    }

    protected void addPawnUsedSpecialMove(CasePosition to, boolean isValid) {
        if (to == null) {
            return;
        }

        isPawnUsedSpecialMoveMap.put(to, isValid);
    }

    protected void removePawnUsedSpecialMove(CasePosition from) {
        if (from == null) {
            return;
        }

        isPawnUsedSpecialMoveMap.remove(from);
    }

    protected void incrementWhiteTurnNumber() {
        whiteTurnNumber++;
    }

    protected void incrementBlackTurnNumber() {
        blackTurnNumber++;
    }

    protected void changePieceTurnNumber(CasePosition from, CasePosition to) {
        if (ObjectUtils.anyNull(to, from)) {
            return;
        }

        turnNumberPieceMap.remove(from);
        turnNumberPieceMap.put(to, totalMove);
    }

    protected void incrementTotalMove() {
        totalMove++;
    }

    public void addHistory(MoveHistory move) {
        moveHistoryList.add(move);
    }

    public void setPiecesGameState(Map<CasePosition, Boolean> isPawnUsedSpecialMoveMap,
                                   Map<CasePosition, Integer> turnNumberPieceMap,
                                   Map<CasePosition, Boolean> isPiecesMovedMap) {

        if (Objects.isNull(isPawnUsedSpecialMoveMap) || Objects.isNull(turnNumberPieceMap) || Objects.isNull(isPiecesMovedMap)) {
            return;
        }

        this.isPawnUsedSpecialMoveMap = new EnumMap<>(isPawnUsedSpecialMoveMap);
        this.turnNumberPieceMap = new EnumMap<>(turnNumberPieceMap);
        this.isPiecesMovedMap = new EnumMap<>(isPiecesMovedMap);
    }

    /**
     * Remove a piece from the board
     *
     * @param from
     */
    public final void removePieceFromBoard(CasePosition from) {
        if (from == null) {
            return;
        }

        positionPiecesMap.remove(from);
        isPiecesMovedMap.remove(from);
        isPawnUsedSpecialMoveMap.remove(from);
        turnNumberPieceMap.remove(from);
    }

    protected void removePawnPromotion(Pair<CasePosition, CasePosition> pair, Side side) {
        if (ObjectUtils.anyNull(pair, side) || Side.OBSERVER.equals(side)) {
            return;
        }

        pawnPromotionMap.remove(side);
    }

    protected final void setPositionPiecesMap(Map<CasePosition, Pieces> positionPiecesMap) {
        if (positionPiecesMap == null || positionPiecesMap.isEmpty()) {
            return;
        }

        this.positionPiecesMap = positionPiecesMap;
        this.defaultPositions.clear();
        //this.defaultPositions.putAll(positionPiecesMap);
        this.defaultPositions.putAll(GameUtils.getDefaultGame());
        this.isPiecesMovedMap = GameUtils.initNewMovedPieceMap(positionPiecesMap);
        this.turnNumberPieceMap = GameUtils.initTurnMap(positionPiecesMap);
    }

    public boolean isGamePaused() {
        return isGamePaused;
    }

    public void setGamePaused(boolean gamePaused) {
        isGamePaused = gamePaused;
    }

    public List<MoveHistory> getMoveHistory() {
        return Collections.unmodifiableList(moveHistoryList);
    }

    public boolean isGameDraw() {
        return isGameDraw;
    }

    @Override
    public GameBoardData clone() throws CloneNotSupportedException {
        GameBoardData cloned = (GameBoardData) super.clone();
        cloned.positionPiecesMap = new EnumMap<>(this.positionPiecesMap);
        cloned.isPiecesMovedMap = new EnumMap<>(this.isPiecesMovedMap);
        cloned.isPawnUsedSpecialMoveMap = new EnumMap<>(this.isPawnUsedSpecialMoveMap);
        cloned.turnNumberPieceMap = new EnumMap<>(this.turnNumberPieceMap);
        cloned.pawnPromotionMap = new ArrayListValuedHashMap<>(this.pawnPromotionMap);
        cloned.totalMove = this.totalMove;
        cloned.blackTurnNumber = this.blackTurnNumber;
        cloned.whiteTurnNumber = this.whiteTurnNumber;
        cloned.isGameDraw = this.isGameDraw;
        cloned.isGamePaused = this.isGamePaused;
        cloned.moveHistoryList = new ArrayList<>(this.moveHistoryList);
        cloned.blackPlayerPoint = this.blackPlayerPoint;
        cloned.whitePlayerPoint = this.whitePlayerPoint;
        cloned.currentAllowedMoveSide = this.currentAllowedMoveSide;
        cloned.isWhiteQueenCastlingAvailable = this.isWhiteQueenCastlingAvailable;
        cloned.isWhiteKingCastlingAvailable = this.isWhiteKingCastlingAvailable;
        cloned.isBlackQueenCastlingAvailable = this.isBlackQueenCastlingAvailable;
        cloned.isBlackKingCastlingAvailable = this.isBlackKingCastlingAvailable;
        cloned.allowOtherToJoin = this.allowOtherToJoin;
        cloned.allowObservers = this.allowObservers;

        return cloned;
    }

    protected final void changeAllowedMoveSide() {
        currentAllowedMoveSide = Side.getOtherPlayerSide(currentAllowedMoveSide);
    }

    public Side getCurrentAllowedMoveSide() {
        return currentAllowedMoveSide;
    }

    protected final void setCurrentAllowedMoveSide(Side side) {
        this.currentAllowedMoveSide = side;
    }

    protected void addBlackPlayerPoint(byte point) {
        blackPlayerPoint += point;
    }

    protected void addWhitePlayerPoint(byte point) {
        whitePlayerPoint += point;
    }

    public short getBlackPlayerPoint() {
        return blackPlayerPoint;
    }

    public short getWhitePlayerPoint() {
        return whitePlayerPoint;
    }

    public final boolean isWhiteQueenCastlingAvailable() {
        return isWhiteQueenCastlingAvailable;
    }

    protected final void setWhiteQueenCastlingAvailable(boolean whiteQueenCastlingAvailable) {
        isWhiteQueenCastlingAvailable = whiteQueenCastlingAvailable;
    }

    public final boolean isWhiteKingCastlingAvailable() {
        return isWhiteKingCastlingAvailable;
    }

    protected final void setWhiteKingCastlingAvailable(boolean whiteKingCastlingAvailable) {
        isWhiteKingCastlingAvailable = whiteKingCastlingAvailable;
    }

    public final boolean isBlackQueenCastlingAvailable() {
        return isBlackQueenCastlingAvailable;
    }

    protected final void setBlackQueenCastlingAvailable(boolean blackQueenCastlingAvailable) {
        isBlackQueenCastlingAvailable = blackQueenCastlingAvailable;
    }

    public final boolean isBlackKingCastlingAvailable() {
        return isBlackKingCastlingAvailable;
    }

    protected final void setBlackKingCastlingAvailable(boolean blackKingCastlingAvailable) {
        isBlackKingCastlingAvailable = blackKingCastlingAvailable;
    }

    public boolean isAllowOtherToJoin() {
        return allowOtherToJoin;
    }

    public void setAllowOtherToJoin(boolean allowOtherToJoin) {
        this.allowOtherToJoin = allowOtherToJoin;
    }

    public boolean isAllowObservers() {
        return allowObservers;
    }

    public void setAllowObservers(boolean allowObservers) {
        this.allowObservers = allowObservers;
    }

    public Pieces getPiece(CasePosition position) {
        return positionPiecesMap.get(position);
    }
}

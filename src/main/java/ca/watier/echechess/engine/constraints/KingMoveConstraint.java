/*
 *    Copyright 2014 - 2017 Yannick Watier
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package ca.watier.echechess.engine.constraints;

import ca.watier.echechess.common.enums.CasePosition;
import ca.watier.echechess.common.enums.MoveType;
import ca.watier.echechess.common.enums.Pieces;
import ca.watier.echechess.common.enums.Side;
import ca.watier.echechess.common.utils.CastlingPositionHelper;
import ca.watier.echechess.common.utils.MathUtils;
import ca.watier.echechess.engine.abstracts.GameBoardData;
import ca.watier.echechess.engine.interfaces.KingHandler;
import ca.watier.echechess.engine.interfaces.MoveConstraint;
import ca.watier.echechess.engine.models.DistancePiecePositionModel;
import ca.watier.echechess.engine.models.enums.MoveStatus;
import ca.watier.echechess.engine.utils.GameUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;

import java.io.Serial;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Created by yannick on 4/23/2017.
 */
public class KingMoveConstraint implements MoveConstraint {

    @Serial
    private static final long serialVersionUID = -7557608272658055902L;

    private final KingHandler kingHandler;

    public KingMoveConstraint(KingHandler kingHandler) {
        this.kingHandler = kingHandler;
    }

    @Override
    public MoveStatus getMoveStatus(CasePosition from, CasePosition to, GameBoardData gameBoardData) {
        Pieces fromPiece = gameBoardData.getPiece(from);
        Pieces toPiece = gameBoardData.getPiece(to);
        
        MoveType moveType = null;
        try {
            moveType = getMoveType(from, to, gameBoardData.clone());
        } catch (CloneNotSupportedException ex) {
        	return MoveStatus.getInvalidMoveStatusBasedOnTarget(toPiece);
        }
        
        int distance = MathUtils.getDistanceBetweenPositionsWithCommonDirection(from, to);
        if ((MoveType.CASTLING.equals(moveType)) && ((distance == 3) || (distance == 4))) {
        	return MoveStatus.VALID_MOVE;
        } else if (distance != 1) {
            return MoveStatus.getInvalidMoveStatusBasedOnTarget(toPiece);
        }

        boolean isSameSideAsTarget = Pieces.isSameSide(fromPiece, toPiece);

        if (!isSameSideAsTarget && Pieces.isKing(toPiece) && Pieces.isKing(fromPiece)) {
            return MoveStatus.KING_ATTACK_KING;
        } else if (!isSameSideAsTarget && Pieces.isKing(toPiece)) {
            return MoveStatus.VALID_ATTACK;
        } else if (isSameSideAsTarget) {
            return MoveStatus.CAN_PROTECT_FRIENDLY;
        } else if (Objects.isNull(toPiece)) {
            return MoveStatus.VALID_MOVE;
        } else {
            return MoveStatus.VALID_ATTACK;
        }
    }


    /*
       --------- Castling ---------
       URL: https://en.wikipedia.org/wiki/Castling
       Castling is permissible if and only if all of the following conditions hold (Schiller 2003:19):
           The king and the chosen rook are on the player's first rank.
           Neither the king nor the chosen rook has previously moved.
           There are no pieces between the king and the chosen rook.
           The king is not currently in check.
           The king does not pass through a square that is attacked by an enemy piece.
           The king does not end up in check. (True of any legal move.)
    */
    @Override
    public MoveType getMoveType(CasePosition from, CasePosition to, GameBoardData gameBoardData) {
        if (ObjectUtils.anyNull(from, to, gameBoardData)) {
            return MoveType.MOVE_NOT_ALLOWED;
        }

        Pieces pieceFrom = gameBoardData.getPiece(from);
        Side sideFrom = pieceFrom.getSide();
        Pieces pieceTo = gameBoardData.getPiece(to);
        Map<CasePosition, Pieces> piecesLocation = gameBoardData.getPiecesLocation();

        if (pieceTo == null) {
            return MoveType.NORMAL_MOVE;
        } else if (isCastlingPieces(pieceFrom, pieceTo)) {
            try {
                return handleCastling(from, to, sideFrom, piecesLocation, gameBoardData, kingHandler);
            } catch (CloneNotSupportedException e) {
                return MoveType.MOVE_NOT_ALLOWED;
            }
        } else {
            return MoveType.NORMAL_MOVE;
        }
    }


    private MoveType handleCastling(CasePosition from, CasePosition to, Side sideFrom, Map<CasePosition, Pieces> piecesLocation, GameBoardData gameHandler, KingHandler kingHandler) throws CloneNotSupportedException {

        CastlingPositionHelper castlingPositionHelper = new CastlingPositionHelper(from, to, sideFrom).invoke();

        if (isCastlingAvailable(gameHandler, castlingPositionHelper, sideFrom)) {
            return MoveType.MOVE_NOT_ALLOWED;
        } else if (isCastlingValid(gameHandler, piecesLocation, castlingPositionHelper, kingHandler, from, sideFrom, to)) {
            return MoveType.CASTLING;
        } else {
            return MoveType.NORMAL_MOVE;
        }
    }

    private boolean isCastlingAvailable(GameBoardData gameHandler, CastlingPositionHelper castlingPositionHelper, Side sideFrom) {
        boolean queenSideCastling = castlingPositionHelper.isQueenSide();
        boolean kingSideCastling = !queenSideCastling;

        boolean isQueenSideAvail = false;
        boolean isKingSideAvail = false;

        switch (sideFrom) {
            case BLACK -> {
                isKingSideAvail = gameHandler.isBlackKingCastlingAvailable();
                isQueenSideAvail = gameHandler.isBlackQueenCastlingAvailable();
            }
            case WHITE -> {
                isKingSideAvail = gameHandler.isWhiteKingCastlingAvailable();
                isQueenSideAvail = gameHandler.isWhiteQueenCastlingAvailable();
            }
        }

        return (queenSideCastling && !isQueenSideAvail) || (kingSideCastling && !isKingSideAvail);
    }

    private boolean isCastlingValid(GameBoardData gameBoardData,
                                    Map<CasePosition, Pieces> piecesLocation,
                                    CastlingPositionHelper castlingPositionHelper,
                                    KingHandler kingHandler,
                                    CasePosition from,
                                    Side sideFrom,
                                    CasePosition to) throws CloneNotSupportedException {


        Set<DistancePiecePositionModel> piecesBetweenKingAndRook = GameUtils.getPiecesBetweenPosition(from, to, piecesLocation);
        CasePosition kingPosition = castlingPositionHelper.getKingPosition();
        CasePosition positionWhereKingPass = castlingPositionHelper.getRookPosition();

        Side otherPlayerSide = Side.getOtherPlayerSide(sideFrom);

        boolean isPieceAreNotMoved = !gameBoardData.isPieceMoved(from) && !gameBoardData.isPieceMoved(to);
        boolean isNoPieceBetweenKingAndRook = CollectionUtils.isEmpty(piecesBetweenKingAndRook);
        boolean isNoPieceAttackingBetweenKingAndRook = CollectionUtils.isEmpty(kingHandler.getPositionsThatCanMoveOrAttackPosition(positionWhereKingPass, otherPlayerSide, gameBoardData.clone()));


        if (!isPieceAreNotMoved || !isNoPieceBetweenKingAndRook || !isNoPieceAttackingBetweenKingAndRook) {
            return false;
        }


        List<CasePosition> positionsThatCanHitCurrentLocation = kingHandler.getPositionsThatCanMoveOrAttackPosition(
                from,
                otherPlayerSide,
                gameBoardData
        );

        if (CollectionUtils.isNotEmpty(positionsThatCanHitCurrentLocation)) {
            return false;
        }


        List<CasePosition> positionsThatCanHitEndPosition = kingHandler.getPositionsThatCanMoveOrAttackPosition(
                kingPosition,
                otherPlayerSide,
                gameBoardData
        );

        return !CollectionUtils.isNotEmpty(positionsThatCanHitEndPosition);
    }

    private boolean isCastlingPieces(Pieces pieceFrom, Pieces pieceTo) {
        return Pieces.isSameSide(pieceFrom, pieceTo) && Pieces.isKing(pieceFrom) && Pieces.isRook(pieceTo);
    }
}

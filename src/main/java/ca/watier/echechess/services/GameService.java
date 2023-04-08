/*
 *    Copyright 2014 - 2021 Yannick Watier
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

package ca.watier.echechess.services;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import ca.watier.echechess.common.enums.CasePosition;
import ca.watier.echechess.common.enums.Side;
import ca.watier.echechess.common.pojos.MoveHistory;
import ca.watier.echechess.common.responses.BooleanResponse;
import ca.watier.echechess.common.sessions.Player;
import ca.watier.echechess.engine.engines.GenericGameHandler;
import ca.watier.echechess.engine.exceptions.FenParserException;
import ca.watier.echechess.exceptions.GameException;
import ca.watier.echechess.models.PawnPromotionPiecesModel;
import ca.watier.echechess.models.PieceLocationModel;
import ca.watier.echechess.models.UserDetailsImpl;
import ca.watier.echechess.types.EndType;

public interface GameService {
    UUID createNewGame(String specialGamePieces, Side side, boolean againstComputer, boolean observers, Player player) throws FenParserException, GameException;

    void movePiece(CasePosition from, CasePosition to, String uuid, Player player) throws GameException;

    GenericGameHandler getGameFromUuid(String uuid) throws GameException;

    Side getPlayerSide(String uuid, Player player) throws GameException;

    void getAllAvailableMoves(CasePosition from, String uuid, Player player) throws GameException;

    BooleanResponse joinGame(String uuid, Side side, String uiUuid, Player player) throws GameException;

    List<PieceLocationModel> getIterableBoard(String uuid, Player player) throws GameException;

    boolean setSideOfPlayer(Side side, String uuid, Player player) throws GameException;

    boolean upgradePiece(CasePosition to, String uuid, PawnPromotionPiecesModel piece, Player player) throws GameException;

    Map<UUID, GenericGameHandler> getAllGames();
    
    boolean deleteGame(String uuid);

    List<CasePosition> getAllAvailableMovesBody(CasePosition from, String uuid, Player player) throws GameException;

    Boolean isPlayerTurn(String uuid, Player player) throws GameException;

    Boolean underCheckMate(String uuid, Player player, Side side) throws GameException;

    List<MoveHistory> getMoveHistory(String uuid) throws GameException;

    EndType isGameEnded(String uuid, Player player) throws GameException;
}

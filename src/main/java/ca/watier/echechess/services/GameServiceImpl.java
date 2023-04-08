/*
 *    Copyright 2014 - 2018 Yannick Watier
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

import static ca.watier.echechess.common.enums.ChessEventMessage.GAME_WON_EVENT_MOVE;
import static ca.watier.echechess.common.enums.ChessEventMessage.PAWN_PROMOTION;
import static ca.watier.echechess.common.enums.ChessEventMessage.PLAYER_JOINED;
import static ca.watier.echechess.common.enums.ChessEventMessage.PLAYER_TURN;
import static ca.watier.echechess.common.enums.ChessEventMessage.REFRESH_BOARD;
import static ca.watier.echechess.common.enums.ChessEventMessage.SCORE_UPDATE;
import static ca.watier.echechess.common.enums.ChessEventMessage.TRY_JOIN_GAME;
import static ca.watier.echechess.common.enums.Side.getOtherPlayerSide;
import static ca.watier.echechess.common.utils.Constants.GAME_ENDED;
import static ca.watier.echechess.common.utils.Constants.GAME_PAUSED_PAWN_PROMOTION;
import static ca.watier.echechess.common.utils.Constants.JOINING_GAME;
import static ca.watier.echechess.common.utils.Constants.NEW_PLAYER_JOINED_SIDE;
import static ca.watier.echechess.common.utils.Constants.NOT_AUTHORIZED_TO_JOIN;
import static ca.watier.echechess.common.utils.Constants.PLAYER_KING_STALEMATE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import ca.watier.echechess.types.EndType;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

import ca.watier.echechess.common.enums.CasePosition;
import ca.watier.echechess.common.enums.KingStatus;
import ca.watier.echechess.common.enums.Pieces;
import ca.watier.echechess.common.enums.Side;
import ca.watier.echechess.common.pojos.MoveHistory;
import ca.watier.echechess.common.responses.BooleanResponse;
import ca.watier.echechess.common.services.WebSocketService;
import ca.watier.echechess.common.sessions.Player;
import ca.watier.echechess.common.utils.Constants;
import ca.watier.echechess.communication.redis.interfaces.GameRepository;
import ca.watier.echechess.communication.redis.model.GenericGameHandlerWrapper;
import ca.watier.echechess.components.CasePositionPiecesMapEntryComparator;
import ca.watier.echechess.delegates.GameMessageDelegate;
import ca.watier.echechess.engine.delegates.PieceMoveConstraintDelegate;
import ca.watier.echechess.engine.engines.GenericGameHandler;
import ca.watier.echechess.engine.exceptions.FenParserException;
import ca.watier.echechess.engine.interfaces.GameEventEvaluatorHandler;
import ca.watier.echechess.engine.utils.FenGameParser;
import ca.watier.echechess.exceptions.GameException;
import ca.watier.echechess.exceptions.GameNotFoundException;
import ca.watier.echechess.exceptions.InvalidGameParameterException;
import ca.watier.echechess.models.PawnPromotionPiecesModel;
import ca.watier.echechess.models.PieceLocationModel;
import ca.watier.echechess.utils.MessageQueueUtils;


/**
 * Created by yannick on 4/17/2017.
 */
public class GameServiceImpl implements GameService {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(GameServiceImpl.class);
    private static final BooleanResponse NO = BooleanResponse.NO;

    private static final CasePositionPiecesMapEntryComparator PIECE_LOCATION_COMPARATOR =
            new CasePositionPiecesMapEntryComparator();

    private final PieceMoveConstraintDelegate pieceMoveConstraintDelegate;
    private final WebSocketService webSocketService;
    private final GameRepository<GenericGameHandler> gameRepository;
    private final GameMessageDelegate gameMessageDelegate;
    private final GameEventEvaluatorHandler gameEvaluator;

    public GameServiceImpl(PieceMoveConstraintDelegate pieceMoveConstraintDelegate,
                           WebSocketService webSocketService,
                           GameRepository<GenericGameHandler> gameRepository,
                           GameMessageDelegate gameMessageDelegate,
                           GameEventEvaluatorHandler gameEvaluator) {

        this.pieceMoveConstraintDelegate = pieceMoveConstraintDelegate;
        this.webSocketService = webSocketService;
        this.gameRepository = gameRepository;
        this.gameMessageDelegate = gameMessageDelegate;
        this.gameEvaluator = gameEvaluator;
    }

    /**
     * Create a new game, and associate it to the player
     *
     * @param specialGamePieces - If null, create a {@link GenericGameHandler}
     * @param side
     * @param againstComputer
     * @param observers
     * @param player
     */
    @Override
    public UUID createNewGame(String specialGamePieces, Side side, boolean againstComputer, boolean observers, Player player) throws FenParserException, GameException {
        if (ObjectUtils.anyNull(player, side)) {
            throw new InvalidGameParameterException();
        }

        GenericGameHandler genericGameHandler;
        if (StringUtils.isNotBlank(specialGamePieces)) {
            genericGameHandler = FenGameParser.parse(specialGamePieces);
        } else {
            genericGameHandler = GenericGameHandler.newStandardHandlerFromConstraintDelegate(pieceMoveConstraintDelegate);
        }

        UUID uui = UUID.randomUUID();
        String uuidAsString = uui.toString();
        genericGameHandler.setUuid(uuidAsString);
        player.addCreatedGame(uui);

        genericGameHandler.setPlayerToSide(player, side);
        genericGameHandler.setAllowOtherToJoin(!againstComputer);
        genericGameHandler.setAllowObservers(observers);

        gameRepository.add(new GenericGameHandlerWrapper<>(uuidAsString, genericGameHandler));

        return uui;
    }

    /**
     * Moves the piece to the specified location
     *
     * @param from
     * @param to
     * @param uuid
     * @param player
     * @return
     */
    @Override
    public void movePiece(CasePosition from, CasePosition to, String uuid, Player player) throws GameException {
        if (ObjectUtils.anyNull(from, to, uuid, player)) {
            throw new InvalidGameParameterException();
        }

        GenericGameHandler gameFromUuid = getGameFromUuid(uuid);
        Side playerSide = getPlayerSide(uuid, player);

        if (!gameFromUuid.hasPlayer(player) || gameFromUuid.isGamePaused() || gameFromUuid.isGameDraw()) {
            return;
        } else if (gameFromUuid.isGameEnded()) {
            webSocketService.fireSideEvent(uuid, playerSide, GAME_WON_EVENT_MOVE, GAME_ENDED);
            return;
        } else if (gameFromUuid.isKing(KingStatus.STALEMATE, playerSide)) {
            webSocketService.fireSideEvent(uuid, playerSide, GAME_WON_EVENT_MOVE, PLAYER_KING_STALEMATE);
            return;
        }

        //UUID|FROM|TO|ID_PLAYER_SIDE
        String payload =  MessageQueueUtils.convertToMessage(uuid, from, to, playerSide.getValue());
        gameMessageDelegate.handleMoveMessage(payload);
    }

    /**
     * Get the game associated to the uuid
     *
     * @param uuid
     * @throws {@link GameNotFoundException} when the game is not found, or {@link InvalidGameParameterException}
     * when any of the required parameters are invalid.
     */
    @Override
    public GenericGameHandler getGameFromUuid(String uuid) throws GameException {
        if (StringUtils.isBlank(uuid)) {
            throw new InvalidGameParameterException();
        }

        return Optional.ofNullable(gameRepository.get(uuid))
                .map(GenericGameHandlerWrapper::getGenericGameHandler)
                .orElseThrow(GameNotFoundException::new);
    }

    /**
     * Get the side of the player for the associated game
     *
     * @param uuid
     * @return
     */
    @Override
    public Side getPlayerSide(String uuid, Player player) throws GameException {
        if (StringUtils.isBlank(uuid)) {
            throw new InvalidGameParameterException();
        }

        return getGameFromUuid(uuid).getPlayerSide(player);
    }

    /**
     * Gets all possible moves for the selected piece
     *
     * @param from
     * @param uuid
     * @param player
     * @return
     */
    @Override
    public void getAllAvailableMoves(CasePosition from, String uuid, Player player) throws GameException {
        if (ObjectUtils.anyNull(from, player) || StringUtils.isBlank(uuid)) {
            throw new InvalidGameParameterException();
        }

        GenericGameHandler gameFromUuid = getGameFromUuid(uuid);
        Side playerSide = gameFromUuid.getPlayerSide(player);

        if (!gameFromUuid.hasPlayer(player) || !isPlayerSameColorThanPiece(from, gameFromUuid, playerSide)) {
            return;  //TODO: Add a checked exception
        }

        String payload = MessageQueueUtils.convertToMessage(uuid, from.name(), playerSide.getValue());
        gameMessageDelegate.handleAvailableMoveMessage(payload);
    }

    private boolean isPlayerSameColorThanPiece(CasePosition from, GenericGameHandler gameFromUuid, Side playerSide) {
        return Optional.ofNullable(gameFromUuid.getPiece(from))
                .map(p -> p.getSide().equals(playerSide))
                .orElse(false);
    }

    @Override
    public BooleanResponse joinGame(String uuid, Side side, String uiUuid, Player player) throws GameException {
        if (StringUtils.isBlank(uiUuid) || player == null || StringUtils.isBlank(uuid)) {
            throw new InvalidGameParameterException();
        }

        boolean joined = false;
        GenericGameHandler gameFromUuid = getGameFromUuid(uuid);

        if (isNotAllowedToJoinGame(side, gameFromUuid)) {
            webSocketService.fireUiEvent(uiUuid, TRY_JOIN_GAME, NOT_AUTHORIZED_TO_JOIN);
            return NO;
        }

        UUID gameUuid = UUID.fromString(uuid);
        if (!player.getCreatedGameList().contains(gameUuid) && !player.getJoinedGameList().contains(gameUuid)) {
            joined = gameFromUuid.setPlayerToSide(player, side);
        }

        if (joined) {
            webSocketService.fireGameEvent(uuid, PLAYER_JOINED, String.format(NEW_PLAYER_JOINED_SIDE, side));
            webSocketService.fireUiEvent(uiUuid, PLAYER_JOINED, String.format(JOINING_GAME, uuid));
            player.addJoinedGame(gameUuid);

            gameRepository.add(new GenericGameHandlerWrapper<>(uuid, gameFromUuid));
        }

        return BooleanResponse.getResponse(joined);
    }

    private boolean isNotAllowedToJoinGame(Side side, GenericGameHandler gameFromUuid) {
        boolean allowObservers = gameFromUuid.isAllowObservers();
        boolean allowOtherToJoin = gameFromUuid.isAllowOtherToJoin();

        return (!allowOtherToJoin && !allowObservers) ||
                (allowOtherToJoin && !allowObservers && Side.OBSERVER.equals(side)) ||
                (!allowOtherToJoin && (Side.BLACK.equals(side) || Side.WHITE.equals(side))) ||
                (allowOtherToJoin && !allowObservers && Objects.nonNull(gameFromUuid.getPlayerWhite()) &&
                        Objects.nonNull(gameFromUuid.getPlayerBlack()));
    }

    @Override
    public List<PieceLocationModel> getIterableBoard(String uuid, Player player) throws GameException {
        if (player == null || StringUtils.isBlank(uuid)) {
            throw new InvalidGameParameterException();
        }

        GenericGameHandler gameFromUuid = getGameFromUuid(uuid);

        if (!gameFromUuid.hasPlayer(player)) {
            return Collections.emptyList();
        }

        // Keys are sorted by small values fist (-3 -> 4)
        Map<Integer, Set<Map.Entry<CasePosition, Pieces>>> sortedByCol = new TreeMap<>(Comparator.naturalOrder());
        Map<CasePosition, Pieces> piecesLocation = new EnumMap<>(gameFromUuid.getPiecesLocation());

        // Fill the empty positions
        for (CasePosition value : CasePosition.values()) {
            if (!piecesLocation.containsKey(value)) {
                piecesLocation.put(value, null);
            }
        }

        // Add the values to the map to be sorted
        for (Map.Entry<CasePosition, Pieces> casePositionPiecesEntry : piecesLocation.entrySet()) {
            CasePosition key = casePositionPiecesEntry.getKey();
            Set<Map.Entry<CasePosition, Pieces>> pairs = sortedByCol.computeIfAbsent(key.getY(), k -> new TreeSet<>(PIECE_LOCATION_COMPARATOR));
            pairs.add(casePositionPiecesEntry);
        }

        // Convert the` java.util.Map.Entry` to `ca.watier.echechess.models.ui.PieceLocationUiModel`
        List<List<PieceLocationModel>> sortedBoardWithColumns = new ArrayList<>();
        for (Integer key : sortedByCol.keySet()) {
            List<PieceLocationModel> currentRow = new ArrayList<>(8);
            for (Map.Entry<CasePosition, Pieces> entry : sortedByCol.get(key)) {
                currentRow.add(new PieceLocationModel(entry.getValue(), entry.getKey()));
            }

            sortedBoardWithColumns.add(currentRow);
        }

        // reverse the board, to be easier to draw
        Collections.reverse(sortedBoardWithColumns);

        List<PieceLocationModel> sortedBoard = new ArrayList<>();

        // merge the board
        for (List<PieceLocationModel> currentRow : sortedBoardWithColumns) {
            if (CollectionUtils.isNotEmpty(currentRow)) {
                sortedBoard.addAll(currentRow);
            }
        }

        return sortedBoard;
    }

    @Override
    public boolean setSideOfPlayer(Side side, String uuid, Player player) throws GameException {
        return getGameFromUuid(uuid).setPlayerToSide(player, side);
    }

    /**
     * Used when we need to upgrade a piece in the board (example: pawn promotion)
     *
     * @param uuid
     * @param piece
     * @param player
     * @return
     */
    @Override
    public boolean upgradePiece(CasePosition to, String uuid, PawnPromotionPiecesModel piece, Player player) throws GameException {
        if (player == null || StringUtils.isBlank(uuid) || piece == null || to == null) {
            throw new InvalidGameParameterException();
        }

        GenericGameHandler gameFromUuid = getGameFromUuid(uuid);
        Side playerSide = gameFromUuid.getPlayerSide(player);

        webSocketService.fireSideEvent(uuid, playerSide, PAWN_PROMOTION, to.name());
        webSocketService.fireGameEvent(uuid, PAWN_PROMOTION, String.format(GAME_PAUSED_PAWN_PROMOTION, playerSide));

        boolean isUpgraded = false;

        try {
            Pieces pieces = PawnPromotionPiecesModel.from(piece, playerSide);
            isUpgraded = gameFromUuid.upgradePiece(to, pieces, playerSide);

            if (isUpgraded) {
                webSocketService.fireGameEvent(uuid, SCORE_UPDATE, gameFromUuid.getGameScore()); //Refresh the points
                webSocketService.fireGameEvent(uuid, REFRESH_BOARD); //Refresh the boards
                webSocketService.fireSideEvent(uuid, getOtherPlayerSide(playerSide), PLAYER_TURN, Constants.PLAYER_TURN);
            }

        } catch (IllegalArgumentException ex) {
            LOGGER.error(ex.toString(), ex);
        }

        return isUpgraded;
    }

    @Override
    public Map<UUID, GenericGameHandler> getAllGames() {
        Map<UUID, GenericGameHandler> values = new HashMap<>();

        for (GenericGameHandlerWrapper<GenericGameHandler> genericGameHandlerWrapper : gameRepository.getAll()) {
            values.put(UUID.fromString(genericGameHandlerWrapper.getId()), genericGameHandlerWrapper.getGenericGameHandler());
        }

        return values;
    }

    @Override
    public boolean deleteGame(String uuid) {
    	gameRepository.delete(uuid);
    	return true;
    }

    @Override
    public List<CasePosition> getAllAvailableMovesBody(CasePosition from, String uuid, Player player) throws  GameException{
        if (ObjectUtils.anyNull(from, player) || StringUtils.isBlank(uuid)) {
            throw new InvalidGameParameterException();
        }

        GenericGameHandler gameFromUuid = getGameFromUuid(uuid);
        Side playerSide = gameFromUuid.getPlayerSide(player);

        if (!gameFromUuid.hasPlayer(player) || !isPlayerSameColorThanPiece(from, gameFromUuid, playerSide)) {
            return null;  //TODO: Add a checked exception
        }

        return gameFromUuid.getAllAvailableMoves(from, playerSide);
    }

    @Override
    public Boolean isPlayerTurn(String uuid, Player player) throws GameException {

        GenericGameHandler gameFromUuid = getGameFromUuid(uuid);
        Side playerSide = gameFromUuid.getPlayerSide(player);

        return this.gameEvaluator.isPlayerTurn(playerSide, gameFromUuid.getCloneOfCurrentDataState());
    }

    @Override
    public Boolean underCheckMate(String uuid, Player player, Side side) throws GameException {
        GenericGameHandler gameFromUuid = getGameFromUuid(uuid);

        return gameFromUuid.isCheckMate(side);
    }

    @Override
    public EndType isGameEnded(String uuid, Player player) throws GameException {
        GenericGameHandler gameFromUuid = getGameFromUuid(uuid);
        boolean gameEnded = false;
        EndType result = EndType.NO_END;

        /*
         * 2 ways to conclude the game is over:
         *  - Checkmate
         *  - Stalemate or interblock
         *       - No available moves
         *       - 3 times same moves
         *       - Situations where the game cannot end
         *              - King vs King
         *              - King + Knight vs King
         *              - King + Bishop vs King
         */

        // Checkmate
        if (gameFromUuid.isCheckMate(Side.WHITE)) {
        	gameEnded = true;
        	result = EndType.W_CHECKMATE;
        } else if (gameFromUuid.isCheckMate(Side.BLACK)) {
        	gameEnded = true;
        	result = EndType.B_CHECKMATE;
        }
        
        if (!gameEnded) {
            // 3 times same move
            List<MoveHistory> moveHist = gameFromUuid.getMoveHistory();
            boolean repetitiveMoves = false;

            if (moveHist.size() > 11) {
                List<MoveHistory> lastMovements = moveHist.subList(moveHist.size() - 11, moveHist.size() - 1);

                repetitiveMoves = true;
                for (int i = 0; i < (lastMovements.size() / 2); i++) {
                    if (!equalMove(lastMovements.get(i), lastMovements.get(i + 4))) {
                        repetitiveMoves = false;
                        break;
                    }
                }
            }
            
            if (repetitiveMoves) {
            	gameEnded = true;
            	result = EndType.REPETITIVE_MOVES;
            }
        }

        Map<CasePosition, Pieces> whitePieces = gameFromUuid.getPiecesLocation(Side.WHITE);
        Map<CasePosition, Pieces> blackPieces = gameFromUuid.getPiecesLocation(Side.BLACK);
        
        if (!gameEnded) {
            // Stalemate and interblocks
            boolean stalemate = true;
            List<CasePosition> availableMoves;


            // Verify movements for player's pieces
            Side playerSide = getPlayerSide(uuid, player);
            Side otherSide = (Side.WHITE.equals(playerSide) ? Side.BLACK : Side.WHITE);
            for (CasePosition tile : (Side.WHITE.equals(playerSide) ? whitePieces : blackPieces).keySet()) {
                availableMoves = gameFromUuid.getAllAvailableMoves(tile, playerSide);
                
                if (CollectionUtils.isNotEmpty(availableMoves)) {
                    stalemate = false;
                    break;
                }
            }

            // Verify movements for other player
            if (!stalemate) {
                for (CasePosition tile : (Side.WHITE.equals(otherSide) ? whitePieces : blackPieces).keySet()) {
                    availableMoves = gameFromUuid.getAllAvailableMoves(tile, otherSide);
                    
                    if (CollectionUtils.isNotEmpty(availableMoves)) {
                        stalemate = false;
                        break;
                    }
                }
            }
            
            if (stalemate) {
            	gameEnded = true;
            	result = EndType.STALEMATE;
            }
        }
        
        if (!gameEnded) {
            // Detect draw situations
            boolean theoreticalDraw = false;

            // King vs King
            // King vs King and Knight
            // King vs King and Bishop
            // King and Bishop vs King and Bishop (same color)

            //If W/B has 2 pieces both check King and Bishop vs King and Bishop (same color)
            if ((whitePieces.size() == 2) && (blackPieces.size() == 2)) {

                if (whitePieces.containsValue(Pieces.W_KING)
                    && whitePieces.containsValue(Pieces.W_BISHOP)
                    && blackPieces.containsValue(Pieces.B_KING)
                    && blackPieces.containsValue(Pieces.B_BISHOP)) {

                    int wBishopTile = -1;
                    int bBishopTile = -1;

                    for (CasePosition tile : whitePieces.keySet()) {
                        if (whitePieces.get(tile).equals(Pieces.W_BISHOP)) {
                        	wBishopTile = checkTileColor(tile);
                        }
                    }

                    for (CasePosition tile : blackPieces.keySet()) {
                        if (blackPieces.get(tile).equals(Pieces.B_BISHOP)) {
                        	bBishopTile = checkTileColor(tile);
                        }
                    }

                    if (wBishopTile == bBishopTile) {
                    	theoreticalDraw = true;
                    }
                }

            } else if ((whitePieces.size() == 1) && (blackPieces.size() == 2)
            		&& (blackPieces.containsValue(Pieces.B_KNIGHT) || blackPieces.containsValue(Pieces.B_BISHOP))) {

                theoreticalDraw = true;

            } else if ((whitePieces.size() == 2) && (blackPieces.size() == 1)
                    && (whitePieces.containsValue(Pieces.W_KNIGHT) || whitePieces.containsValue(Pieces.W_BISHOP))) {

                theoreticalDraw = true;

            } else if ((whitePieces.size() == 1) && (blackPieces.size() == 1)) {

                theoreticalDraw = true;
            }
            
            if (theoreticalDraw) {
            	gameEnded = true;
            	result = EndType.MATERIAL_DRAW;
            }
        }
        
        return result;
    }

    private boolean equalMove(MoveHistory m1, MoveHistory m2) {
        return m1.getPlayerSide().equals(m2.getPlayerSide())
                && m1.getFrom().equals(m2.getFrom())
                && m1.getTo().equals(m2.getTo())
                && m1.getMoveType().equals(m2.getMoveType());
    }

    private int checkTileColor (CasePosition tile) {
        int color = -1;

        List<CasePosition> whiteTiles = Arrays.asList(
                CasePosition.A2, CasePosition.A4, CasePosition.A6, CasePosition.A8,
                CasePosition.B1, CasePosition.B3, CasePosition.B5, CasePosition.B7,
                CasePosition.C2, CasePosition.C4, CasePosition.C6, CasePosition.C8,
                CasePosition.D1, CasePosition.D3, CasePosition.D5, CasePosition.D7,
                CasePosition.E2, CasePosition.E4, CasePosition.E6, CasePosition.E8,
                CasePosition.F1, CasePosition.F3, CasePosition.F5, CasePosition.F7,
                CasePosition.G2, CasePosition.G4, CasePosition.G6, CasePosition.G8,
                CasePosition.H1, CasePosition.H3, CasePosition.H5, CasePosition.H7);

        List<CasePosition> blackTiles = Arrays.asList(
                CasePosition.A1, CasePosition.A3, CasePosition.A5, CasePosition.A7,
                CasePosition.B2, CasePosition.B4, CasePosition.B6, CasePosition.B8,
                CasePosition.C1, CasePosition.C3, CasePosition.C5, CasePosition.C7,
                CasePosition.D2, CasePosition.D4, CasePosition.D6, CasePosition.D8,
                CasePosition.E1, CasePosition.E3, CasePosition.E5, CasePosition.E7,
                CasePosition.F2, CasePosition.F4, CasePosition.F6, CasePosition.F8,
                CasePosition.G1, CasePosition.G3, CasePosition.G5, CasePosition.G7,
                CasePosition.H2, CasePosition.H4, CasePosition.H6, CasePosition.H8);

        if (whiteTiles.contains(tile)) {
            color = 1;
        } else {
            color = 0;
        }

        return color;
    }
}

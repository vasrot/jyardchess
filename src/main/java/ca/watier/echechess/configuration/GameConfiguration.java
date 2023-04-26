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

package ca.watier.echechess.configuration;


import ca.watier.echechess.common.services.WebSocketService;
import ca.watier.echechess.communication.redis.interfaces.GameRepository;
import ca.watier.echechess.delegates.GameMessageDelegate;
import ca.watier.echechess.engine.delegates.PieceMoveConstraintDelegate;
import ca.watier.echechess.engine.engines.GenericGameHandler;
import ca.watier.echechess.engine.handlers.GameEventEvaluatorHandlerImpl;
import ca.watier.echechess.engine.interfaces.GameEventEvaluatorHandler;
import ca.watier.echechess.services.GameService;
import ca.watier.echechess.services.GameServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GameConfiguration {
    @Bean
    public PieceMoveConstraintDelegate gameMoveConstraintDelegate() {
        return new PieceMoveConstraintDelegate();
    }

    @Bean
    public GameEventEvaluatorHandler gameEvaluator() { return new GameEventEvaluatorHandlerImpl(); }

    @Bean
    public GameService gameService(PieceMoveConstraintDelegate pieceMoveConstraintDelegate,
                                   WebSocketService webSocketService,
                                   GameRepository<GenericGameHandler> gameRepository,
                                   GameMessageDelegate gameMessageDelegate,
                                   GameEventEvaluatorHandler gameEvaluator) {

        return new GameServiceImpl(pieceMoveConstraintDelegate, webSocketService, gameRepository, gameMessageDelegate, gameEvaluator);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}

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

package ca.watier.echechess;

import ca.watier.echechess.common.sessions.Player;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;

import java.util.UUID;

import static ca.watier.echechess.common.utils.CacheConstants.CACHE_UI_SESSION_EXPIRY;
import static org.ehcache.config.builders.CacheConfigurationBuilder.newCacheConfigurationBuilder;

@SpringBootApplication
public class EcheChessApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(EcheChessApplication.class).run(args);
    }

    @Bean
    public CacheConfigurationBuilder<UUID, Player> uuidPlayerCacheConfiguration() {
        return newCacheConfigurationBuilder(UUID.class, Player.class, ResourcePoolsBuilder.heap(100))
                .withExpiry(CACHE_UI_SESSION_EXPIRY);
    }
}

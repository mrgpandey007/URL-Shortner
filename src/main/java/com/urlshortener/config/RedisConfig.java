package com.urlshortener.config;

import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Configuration — this is what separates someone who "used Redis"
 * from someone who understands it.
 *
 * KEY DECISIONS made here:
 *
 * 1. TTL (Time-To-Live): Cache entries expire after 24 hours.
 *    Why? Stale data. If someone updates a URL mapping, the cache must
 *    eventually reflect the change. TTL is the simplest eviction strategy.
 *    A more sophisticated system uses cache invalidation on update.
 *
 * 2. JSON Serialization: We use Jackson to serialize objects to JSON in Redis.
 *    Alternative is Java's native serialization — avoid it. It's brittle,
 *    version-sensitive, and unreadable with redis-cli.
 *
 * 3. Per-cache TTL: Different caches can have different TTLs.
 *    URL redirects (hot path) → 24h TTL
 *    Stats (less critical) → 5m TTL
 */
@Configuration
public class RedisConfig {

    public static final String URL_CACHE = "urls";
    public static final String STATS_CACHE = "stats";

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Keys are simple strings (the short codes)
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Values are JSON-serialized Java objects
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Default config for all caches
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(24))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()))
            .disableCachingNullValues(); // Don't cache null — it would cache 404s

        // Per-cache overrides
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        // URL lookups — cache for 24 hours (URLs rarely change)
        cacheConfigs.put(URL_CACHE, defaultConfig.entryTtl(Duration.ofHours(24)));

        // Stats — cache briefly (they update on every click)
        cacheConfigs.put(STATS_CACHE, defaultConfig.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigs)
            .build();
    }
}

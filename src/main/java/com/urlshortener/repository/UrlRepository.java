package com.urlshortener.repository;

import com.urlshortener.model.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA Repository — Spring generates the SQL for you from method names.
 * But you should know what SQL is actually running under the hood.
 */
@Repository
public interface UrlRepository extends JpaRepository<Url, Long> {

    /**
     * SELECT * FROM urls WHERE short_code = ?
     * This hits the B-tree index on short_code — fast lookup.
     * This is the most critical query in the entire system.
     */
    Optional<Url> findByShortCode(String shortCode);

    /**
     * Check if a custom alias is already taken before inserting.
     * SELECT EXISTS (SELECT 1 FROM urls WHERE short_code = ?)
     */
    boolean existsByShortCode(String shortCode);

    /**
     * Check if we've already shortened this exact URL before.
     * Avoids creating duplicate entries for the same original URL.
     * Optimization: return existing short code instead of generating a new one.
     */
    Optional<Url> findByOriginalUrl(String originalUrl);

    /**
     * Increment click count in-place without loading the entity.
     * Why? Loading the entity just to increment a number is wasteful.
     * UPDATE urls SET click_count = click_count + 1 WHERE short_code = ?
     * @Modifying is required for UPDATE/DELETE queries.
     */
    @Modifying
    @Query("UPDATE Url u SET u.clickCount = u.clickCount + 1 WHERE u.shortCode = :shortCode")
    void incrementClickCount(@Param("shortCode") String shortCode);
}

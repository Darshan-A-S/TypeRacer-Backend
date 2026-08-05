package com.example.typeracer.repository;

import com.example.typeracer.entities.GameResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GameResultRepository extends JpaRepository<GameResult, Long> {

    @Query("select count(r) from GameResult r where r.user.id = :userId")
    long countGames(@Param("userId") Long userId);

    @Query("select count(r) from GameResult r where r.user.id = :userId and r.won = true")
    long countWins(@Param("userId") Long userId);

    @Query("select avg(r.wpm) from GameResult r where r.user.id = :userId")
    Double avgWpm(@Param("userId") Long userId);

    @Query("select max(r.wpm) from GameResult r where r.user.id = :userId")
    Double bestWpm(@Param("userId") Long userId);

    @Query("select avg(r.accuracy) from GameResult r where r.user.id = :userId")
    Double avgAccuracy(@Param("userId") Long userId);

    @Query("select r from GameResult r where r.user.id = :userId order by r.id desc")
    List<GameResult> findRecentByUserId(@Param("userId") Long userId, Pageable pageable);
}

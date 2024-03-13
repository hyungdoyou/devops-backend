package com.example.bootshelf.boardsvc.board.repository;

import com.example.bootshelf.boardsvc.board.model.entity.Board;
import com.example.bootshelf.boardsvc.board.repository.querydsl.BoardRepositoryCustom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BoardRepository extends JpaRepository<Board, Integer>, BoardRepositoryCustom {
    Optional<Board> findByBoardTitle(String boardTitle);
    Optional<Board> findByIdx(Integer boardIdx);

    Optional<Board> findByIdxAndUserIdx(Integer boardIdx, Integer userIdx);

}
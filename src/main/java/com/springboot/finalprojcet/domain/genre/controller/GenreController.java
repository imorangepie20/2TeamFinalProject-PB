package com.springboot.finalprojcet.domain.genre.controller;

import com.springboot.finalprojcet.domain.genre.dto.GenreDto;
import com.springboot.finalprojcet.domain.genre.dto.UserGenreDto;
import com.springboot.finalprojcet.domain.genre.service.GenreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/genres")
@Tag(name = "Genre", description = "장르 API")
@RequiredArgsConstructor
public class GenreController {

    private final GenreService genreService;

    @GetMapping
    @Operation(summary = "장르 목록 조회 (그룹핑)", description = "카테고리별로 그룹핑된 장르 목록을 조회합니다.")
    public ResponseEntity<Map<String, Object>> getGenresGrouped() {
        return ResponseEntity.ok(genreService.getGenresGrouped());
    }

    @GetMapping("/flat")
    @Operation(summary = "장르 목록 조회 (플랫)", description = "모든 장르를 플랫 리스트로 조회합니다.")
    public ResponseEntity<Map<String, Object>> getFlatGenres() {
        List<GenreDto> genres = genreService.getFlatGenres();
        return ResponseEntity.ok(Map.of("success", true, "genres", genres));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "사용자 선호 장르 조회", description = "특정 사용자의 선호 장르를 조회합니다.")
    public ResponseEntity<Map<String, Object>> getUserGenres(@PathVariable Long userId) {
        List<UserGenreDto> genres = genreService.getUserGenres(userId);
        return ResponseEntity.ok(Map.of("success", true, "genres", genres));
    }
}

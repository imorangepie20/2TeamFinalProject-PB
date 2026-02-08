package com.springboot.finalprojcet.domain.cart.repository;

import com.springboot.finalprojcet.entity.UserCart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserCartRepository extends JpaRepository<UserCart, Long> {
    
    List<UserCart> findByUserUserIdOrderByCreatedAtDesc(Long userId);
    
    Optional<UserCart> findByUserUserIdAndTitleAndArtist(Long userId, String title, String artist);
    
    Optional<UserCart> findByIdAndUserUserId(Long id, Long userId);
    
    void deleteByUserUserId(Long userId);
    
    int countByUserUserId(Long userId);
    
    boolean existsByUserUserIdAndTitleAndArtist(Long userId, String title, String artist);
}

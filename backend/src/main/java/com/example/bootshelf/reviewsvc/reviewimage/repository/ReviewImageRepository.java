package com.example.bootshelf.reviewsvc.reviewimage.repository;

import com.example.bootshelf.reviewsvc.reviewimage.model.ReviewImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewImageRepository extends JpaRepository<ReviewImage, Integer> {
}
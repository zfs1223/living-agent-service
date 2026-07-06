package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.InternalReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InternalReviewRepository extends JpaRepository<InternalReviewEntity, Long> {

    Optional<InternalReviewEntity> findByReviewId(String reviewId);

    List<InternalReviewEntity> findByTodoItemIdOrderByReviewRoundAsc(String todoItemId);

    List<InternalReviewEntity> findByTodoItemIdAndStatus(String todoItemId, String status);

    Optional<InternalReviewEntity> findTopByTodoItemIdOrderByReviewRoundDesc(String todoItemId);
}

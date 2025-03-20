package com.users.service.repositories;

import com.users.service.documents.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Page<User> findAllByIdIn(List<String> ids, Pageable pageable);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}

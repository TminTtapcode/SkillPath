package com.skillpath.user.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface UserProfileJpaRepository extends JpaRepository<UserProfileEntity, Long> {}

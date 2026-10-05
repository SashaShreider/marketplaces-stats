package ru.analizer.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.analizer.auth.domain.AppUser;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByLogin(String login);

    boolean existsByLogin(String login);
}
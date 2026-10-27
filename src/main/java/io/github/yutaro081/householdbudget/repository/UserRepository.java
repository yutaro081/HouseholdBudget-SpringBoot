package io.github.yutaro081.householdbudget.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.yutaro081.householdbudget.entity.User;

/**
 * users の読み書き（設計書 4.6）。
 * 大文字・小文字を同じとみなす比べ方は、DB の照合順序で決まる（Q10、3.12）。
 */
public interface UserRepository extends JpaRepository<User, Long> {

	/** ユーザー名で1件探す（ログイン、CustomUserDetailsService が使う）。 */
	Optional<User> findByUsername(String username);

	/** 同じユーザー名があるか（アカウント登録の重複確認、RegistrationService が使う）。 */
	boolean existsByUsername(String username);

}

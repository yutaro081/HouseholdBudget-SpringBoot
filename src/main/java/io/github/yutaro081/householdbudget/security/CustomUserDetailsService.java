package io.github.yutaro081.householdbudget.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import io.github.yutaro081.householdbudget.repository.UserRepository;

/**
 * ログイン時に、ユーザー名から users を探して Spring Security に渡す（設計書 4.5、5.2）。
 * パスワードの照合（BCrypt）は Spring Security が行う。
 * 「ユーザー名がない」と「パスワードが違う」は、同じ失敗として扱われ、同じメッセージになる（B8）。
 * ユーザー名の大文字・小文字は、DB の照合順序で同じとみなされる（Q10、3.12）。
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	public CustomUserDetailsService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		return userRepository.findByUsername(username)
				.map(user -> new LoginUser(user.getId(), user.getUsername(), user.getPasswordHash()))
				.orElseThrow(() -> new UsernameNotFoundException("user not found"));
	}

}

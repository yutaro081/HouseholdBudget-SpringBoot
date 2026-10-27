package io.github.yutaro081.householdbudget.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * アプリ全体の設定（設計書 4.5、P2）。
 * 日本時間（B6）の「今日」を1か所で決めるため、日本時間の Clock を用意する。
 * 「今日」が必要な処理は、LocalDate.now() を直接呼ばず、この Clock を受け取って使う
 * （テストで日付を固定できるようにするため）。
 */
@Configuration
public class AppConfig {

	/** 日本時間のタイムゾーン */
	public static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo");

	@Bean
	public Clock clock() {
		return Clock.system(JAPAN);
	}

}

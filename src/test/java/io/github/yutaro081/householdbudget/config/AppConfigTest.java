package io.github.yutaro081.householdbudget.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

/** 日本時間の Clock の単体テスト（設計書 B6、P2）。Spring もデータベースも使わない。 */
class AppConfigTest {

	@Test
	void clockIsJapanTime() {
		Clock clock = new AppConfig().clock();

		assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Tokyo"));
	}

	/** 世界標準時（UTC）で2月28日の16時は、日本時間では3月1日の1時。日付が日本時間で決まることの確認 */
	@Test
	void dateFollowsJapanTime() {
		Clock japan = Clock.fixed(Instant.parse("2027-02-28T16:00:00Z"), AppConfig.JAPAN);

		assertThat(LocalDate.now(japan)).isEqualTo(LocalDate.of(2027, 3, 1));
	}

}

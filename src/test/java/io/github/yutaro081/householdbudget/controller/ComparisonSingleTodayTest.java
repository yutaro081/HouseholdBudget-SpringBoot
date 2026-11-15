package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import io.github.yutaro081.householdbudget.config.AppConfig;
import io.github.yutaro081.householdbudget.domain.Season;
import io.github.yutaro081.householdbudget.domain.SeasonPeriod;
import io.github.yutaro081.householdbudget.security.LoginUser;
import io.github.yutaro081.householdbudget.service.ComparisonService;

/**
 * 予実比較は、1 回の表示の中で「今日」を 1 回だけ取る（設計書 4.4.1、B6、issue 027）。
 * <p>
 * 日付が変わる瞬間（季節の最後の日の 23:59:59 → 次の季節の初日の 0:00:01）に開いても、
 * 「どの季節を出すか」と「その季節が今の季節か（見込みを出すか）」が、同じ日で決まることを確かめる。
 * そのために、呼ぶたびに時刻が進む時計を使う：1 回目は 2026-11-30（秋の最後の日）、2 回目からは 2026-12-01（冬の初日）。
 * 「今日」を 2 回取ると、Service に「秋」と「12/1」が渡り、秋の表を出しながら「今は秋ではない」と判定して、見込みが消える。
 * <p>
 * Spring もデータベースも使わない（Controller を new して、Service は代役）。専用の時計のために Spring の準備（ApplicationContext）を
 * 1 つ増やすと、データベースの接続の数が足りなくなるため（FixedClock20261111Config の説明と同じ理由）。
 */
class ComparisonSingleTodayTest {

	/** 呼ぶたびに進む時計。1 回目だけ 11/30 の 23:59:59、2 回目からは 12/1 の 0:00:01（日本時間） */
	private static class TickingClock extends Clock {

		private static final Instant BEFORE_MIDNIGHT = LocalDateTime.of(2026, 11, 30, 23, 59, 59).atZone(AppConfig.JAPAN).toInstant();
		private static final Instant AFTER_MIDNIGHT = LocalDateTime.of(2026, 12, 1, 0, 0, 1).atZone(AppConfig.JAPAN).toInstant();

		private final AtomicInteger calls = new AtomicInteger();

		@Override
		public Instant instant() {
			return calls.getAndIncrement() == 0 ? BEFORE_MIDNIGHT : AFTER_MIDNIGHT;
		}

		@Override
		public ZoneId getZone() {
			return AppConfig.JAPAN;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		int calls() {
			return calls.get();
		}
	}

	/** 季節を指定せずに開く：1 回目の「今日」（11/30）で秋に決まり、Service にも同じ 11/30 が渡る。時計を呼ぶのは 1 回だけ */
	@Test
	void theSeasonAndTheForecastUseTheSameToday() {
		TickingClock clock = new TickingClock();
		ComparisonService service = mock(ComparisonService.class);
		ComparisonController controller = new ComparisonController(service, clock);
		ExtendedModelMap model = new ExtendedModelMap();

		String template = controller.show(new LoginUser(7L, "cmptoday1", "x"), null, null, model);

		verify(service).view(7L, new SeasonPeriod(2026, Season.AUTUMN), LocalDate.of(2026, 11, 30));
		verifyNoMoreInteractions(service);
		assertThat(clock.calls()).as("「今日」を取るのは 1 回だけ").isEqualTo(1);
		assertThat(template).isEqualTo("comparison/index");
		assertThat(model).containsKey("view");
	}

}

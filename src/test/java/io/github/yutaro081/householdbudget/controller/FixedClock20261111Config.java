package io.github.yutaro081.householdbudget.controller;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import io.github.yutaro081.householdbudget.config.AppConfig;

/**
 * 「今日」を、日本時間の 2026-11-11（水曜。秋の 72 日目。今週は 11/8〜11/14）に固定する Clock（issue 020 のテスト用）。
 * 複数のテストクラスが同じ設定を @Import で共有すると、Spring のテスト用の準備（ApplicationContext）が 1 つで済み、
 * データベースの接続の数が増えすぎない（クラスごとに別の設定を書くと、その分だけ接続が増えて「Too many connections」になる）。
 */
@TestConfiguration
public class FixedClock20261111Config {

	@Bean
	@Primary
	Clock fixedClock() {
		return Clock.fixed(LocalDate.of(2026, 11, 11).atStartOfDay(AppConfig.JAPAN).toInstant(), AppConfig.JAPAN);
	}
}

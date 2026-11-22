package io.github.yutaro081.householdbudget;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.autoconfigure.web.ErrorProperties.IncludeAttribute;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

/**
 * エラーの詳細を画面に含めない設定が、アプリに読まれているかの確認（設計書 7.7、S2、issue 033）。
 * <p>
 * 標準の値（never・false）と書いた値が同じなので、「アプリが読み込んだ値」だけでは、設定が読まれているかを見分けられない
 * （名前を間違えて読まれなくても、標準の値で同じになる）。そのため、次の 3 つを確かめる。
 * <ul>
 * <li>① 設定が、本体の application.properties から読まれ、書いた値になっている（設定の出どころを確かめる）</li>
 * <li>② 古い名前（server.error.include-*。Spring Boot 4.0 から読まれない）が、どこにも書かれていない</li>
 * <li>③ アプリが読み込んだ値（WebProperties の error）が、書いた値になっている</li>
 * </ul>
 */
@SpringBootTest
class ErrorSettingsTest {

	/** 本体の application.properties の出どころの名前に入る文字（テスト用の config/application.properties と区別する） */
	private static final String MAIN_PROPERTIES = "class path resource [application.properties]";

	@Autowired
	private ConfigurableEnvironment environment;

	@Autowired
	private WebProperties webProperties;

	@ParameterizedTest(name = "{0}={1}")
	@CsvSource(delimiter = '|', value = {
			"spring.web.error.include-stacktrace|never",
			"spring.web.error.include-message|never",
			"spring.web.error.include-binding-errors|never",
			"spring.web.error.include-exception|false",
	})
	void 本体のapplication_propertiesから読まれる(String name, String value) {
		assertThat(sourcesContaining(name))
				.as("%s が書かれている設定の出どころ", name)
				.anySatisfy(source -> assertThat(source.getName()).contains(MAIN_PROPERTIES));
		assertThat(environment.getProperty(name)).isEqualTo(value);
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {
			"server.error.include-stacktrace",
			"server.error.include-message",
			"server.error.include-binding-errors",
			"server.error.include-exception",
	})
	void 古い名前はどこにも書かれていない(String name) {
		assertThat(sourcesContaining(name)).as("%s が書かれている設定の出どころ", name).isEmpty();
	}

	@Test
	void アプリが読み込んだ値は_含めない() {
		ErrorProperties error = webProperties.getError();
		assertThat(error.getIncludeStacktrace()).isEqualTo(IncludeAttribute.NEVER);
		assertThat(error.getIncludeMessage()).isEqualTo(IncludeAttribute.NEVER);
		assertThat(error.getIncludeBindingErrors()).isEqualTo(IncludeAttribute.NEVER);
		assertThat(error.isIncludeException()).isFalse();
	}

	/** その名前の設定を持っている、設定の出どころ（ファイルなど）の一覧 */
	private List<PropertySource<?>> sourcesContaining(String name) {
		List<PropertySource<?>> found = new ArrayList<>();
		for (PropertySource<?> source : environment.getPropertySources()) {
			if (source.containsProperty(name)) {
				found.add(source);
			}
		}
		return found;
	}

}

package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * アイコンの部品（fragments/icons.html。設計書 2.3「アイコン」、4.4.2、7.5、issue 017）のテスト。
 * テスト専用の画面（src/test/resources/templates/icons-test.html）から、名前を渡して呼ぶ。
 * アイコンは固定の &lt;svg&gt; だけ。名前は固定の文字だけで渡す。知らない名前では、何も出さない。
 */
@SpringBootTest
class IconsFragmentTest {

	/** 設計書 2.3「アイコン」の 11 個 */
	private static final List<String> NAMES = List.of("logo", "top", "transactions", "categories", "vendors",
			"payment-methods", "budgets", "comparison", "plus", "sun", "pie");

	private static final Pattern SVG = Pattern.compile("<svg.*?</svg>", Pattern.DOTALL);

	@Autowired
	private SpringTemplateEngine engine;

	private String render(List<String> names, Object size, Object stroke) {
		Context context = new Context();
		context.setVariable("names", names);
		context.setVariable("size", size);
		context.setVariable("stroke", stroke);
		return engine.process("icons-test", context);
	}

	private static List<String> svgs(String html) {
		Matcher m = SVG.matcher(html);
		List<String> list = new java.util.ArrayList<>();
		while (m.find()) {
			list.add(m.group());
		}
		return list;
	}

	@Test
	void eachOfTheElevenNamesGivesOneSvg() {
		for (String name : NAMES) {
			List<String> svgs = svgs(render(List.of(name), 18, 2));
			assertThat(svgs).as(name).hasSize(1);
		}
	}

	@Test
	void theElevenIconsAreAllDifferent() {
		Set<String> shapes = new HashSet<>();
		for (String name : NAMES) {
			String svg = svgs(render(List.of(name), 18, 2)).get(0);
			// 形（中身）だけを比べる
			shapes.add(svg.substring(svg.indexOf('>') + 1));
		}
		assertThat(shapes).hasSize(11);
	}

	@Test
	void everyIconIsADecorativeLineIconThatFollowsTheTextColor() {
		for (String name : NAMES) {
			String svg = svgs(render(List.of(name), 18, 2)).get(0);
			assertThat(svg).as(name).contains("viewBox=\"0 0 24 24\"", "fill=\"none\"", "stroke=\"currentColor\"",
					"stroke-linecap=\"round\"", "stroke-linejoin=\"round\"", "aria-hidden=\"true\"", "focusable=\"false\"");
		}
	}

	@Test
	void sizeAndStrokeWidthComeFromTheArguments() {
		String small = svgs(render(List.of("plus"), 18, 2.4)).get(0);
		String big = svgs(render(List.of("sun"), 20, 2)).get(0);

		assertThat(small).contains("width=\"18\"", "height=\"18\"", "stroke-width=\"2.4\"");
		assertThat(big).contains("width=\"20\"", "height=\"20\"", "stroke-width=\"2\"");
	}

	@Test
	void noIconContainsAnythingUnsafeOrExternal() {
		String all = String.join(" ", svgs(render(NAMES, 18, 2)));
		assertThat(svgs(all)).hasSize(11);

		assertThat(all).doesNotContain("<script", "<use", "<image", "xmlns", "style=", "http", "onclick", "onload", "href", "url(");
	}

	@Test
	void anUnknownNameGivesNothing() {
		String html = render(List.of("nonexistent", "<script>alert(1)</script>", ""), 18, 2);

		assertThat(html).doesNotContain("<svg").doesNotContain("<script");
	}

	@Test
	void theLogoAndTheMenuIconsHaveTheShapesOfTheDesign() {
		assertThat(svgs(render(List.of("logo"), 18, 2)).get(0)).contains("<rect x=\"3\" y=\"5\" width=\"18\" height=\"14\" rx=\"2.5\"");
		assertThat(svgs(render(List.of("top"), 18, 2)).get(0)).contains("M3 11l9-7 9 7");
		assertThat(svgs(render(List.of("comparison"), 18, 2)).get(0)).contains("M4 20V10", "M10 20V4", "M16 20v-7", "M22 20H2");
		assertThat(svgs(render(List.of("pie"), 20, 2)).get(0)).contains("M21 12a9 9 0 1 1-9-9v9z");
	}

}

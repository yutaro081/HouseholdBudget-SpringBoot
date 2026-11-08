package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;

/**
 * ヘッダーのメニューの「今開いている項目」を確かめる補助（issue 017）。
 * メニューの項目は「アイコン（&lt;svg&gt;）＋文字」なので、開きタグと文字の間に &lt;svg&gt; が入る。
 * 文字の完全一致ではなく、「&lt;a href=… aria-current="page"&gt;」「&lt;svg&gt;…&lt;/svg&gt;」「文字&lt;/a&gt;」の順で探す。
 */
final class MenuAssertions {

	private MenuAssertions() {
	}

	/** 今開いている項目（aria-current="page"）になっていて、アイコンと文字を持つこと */
	static void assertCurrentItem(String body, String href, String label) {
		Pattern pattern = Pattern.compile("<a href=\"" + Pattern.quote(href) + "\" aria-current=\"page\"><svg[^>]*>.*?</svg>"
				+ Pattern.quote(label) + "</a>", Pattern.DOTALL);
		assertThat(body).as("メニューの「" + label + "」(" + href + ") が、今開いている項目").containsPattern(pattern);
	}

}

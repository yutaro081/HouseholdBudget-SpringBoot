package io.github.yutaro081.householdbudget.config;

import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 静的なフォントのファイルだけ、ブラウザに30日覚えさせる設定（設計書 7.10、issue 015 の 2 回目、Q15-8 の B）。
 * <p>
 * Spring Security は、画面も CSS・JavaScript も含め、すべての応答に {@code Cache-Control: no-store} を付ける。
 * そのままだと、約 2 MB のフォントを、画面を開くたびに取り直すことになる。
 * Spring MVC が静的ファイルに Cache-Control を付けた場合、Spring Security は上書きしない（実際の応答で確かめた）。
 * 対象は {@code /fonts/**} だけ。画面（HTML）と {@code /css/**}・{@code /js/**} は、{@code no-store} のまま。
 * フォントのファイルを入れ替えるときは、30日の間、古いファイルが残らないように、ファイル名を変える。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/fonts/**")
				.addResourceLocations("classpath:/static/fonts/")
				.setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS));
	}

}

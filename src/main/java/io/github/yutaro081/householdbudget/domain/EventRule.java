package io.github.yutaro081.householdbudget.domain;

/**
 * 年中行事の日の決まり方（設計書 5.12）。2種類ある。
 * <ul>
 * <li>月日が決まっているもの（元日、七草、ひな祭り、端午の節句、七夕、お盆、七五三、大晦日）。</li>
 * <li>二十四節気の日から日数を引くもの（節分＝立春の1日前、春と秋のお彼岸入り＝春分・秋分の3日前）。</li>
 * </ul>
 * 十五夜・土用の丑の日は入れていない。あとから足すときは、決まり方の種類を増やす。
 *
 * @param name       行事の名前
 * @param month      月（月日が決まっているもの。そうでなければ0）
 * @param day        日（同上）
 * @param sekkiIndex 節気の位置（SolarTermCalendar.NAMES。月日が決まっているものは -1）
 * @param daysBefore 節気の何日前か（月日が決まっているものは0）
 */
public record EventRule(String name, int month, int day, int sekkiIndex, int daysBefore) {

	public static EventRule fixed(String name, int month, int day) {
		return new EventRule(name, month, day, -1, 0);
	}

	public static EventRule beforeSekki(String name, int sekkiIndex, int daysBefore) {
		return new EventRule(name, 0, 0, sekkiIndex, daysBefore);
	}

	public boolean isFixed() {
		return sekkiIndex < 0;
	}

}

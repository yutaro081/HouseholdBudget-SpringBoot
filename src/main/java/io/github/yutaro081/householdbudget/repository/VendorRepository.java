package io.github.yutaro081.householdbudget.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.yutaro081.householdbudget.entity.Vendor;

/**
 * Vendor の読み書き（設計書 4.6）。
 * <p>
 * 【決まり】すべての検索は user_id で絞る（設計書 3.9）。ID だけで探すメソッド（findById など）は、
 * アプリのコード（Service）から使わない。他人のデータは、見つからないものとして扱う（404）。
 * 名前の比べ方（大文字・小文字、かな、全角半角）は、DB の照合順序（utf8mb4_0900_as_ci）で決まる（設計書 3.12）。
 */
public interface VendorRepository extends JpaRepository<Vendor, Long> {

	/** 自分の取引先を、登録順（id の順）で全件。一覧に使う（Q14）。 */
	List<Vendor> findByUserIdOrderByIdAsc(Long userId);

	/**
	 * 自分の取引先を、取引の登録・編集画面の選択肢の順で全件（要件定義書 4.2、issue 029）。
	 * 全期間の取引の件数が多い順。件数が同じなら id の順（登録順）。取引が 0 件のものは最後（left join なので、0 件も出る）。
	 * 件数は、1 本の検索の中で数える（検索の本数を増やさない）。数える取引も user_id で絞る（ほかの人の取引は数えない。設計書 3.9）。
	 */
	@Query("""
			select x from Vendor x
			left join Transaction t on t.vendor = x and t.userId = :userId
			where x.userId = :userId
			group by x
			order by count(t) desc, x.id asc
			""")
	List<Vendor> findChoicesByUserId(@Param("userId") Long userId);

	/** ID と user_id の両方で1件探す。他人のものや、存在しない ID は見つからない。 */
	Optional<Vendor> findByIdAndUserId(Long id, Long userId);

	/** 自分のものの個数。追加のとき、1人あたりの上限（Limits、256個）に達していないかの確認に使う（R14）。 */
	long countByUserId(Long userId);

	/** 自分の中に、同じ名前があるか。追加の重複確認に使う（設計書 5.6）。 */
	boolean existsByUserIdAndName(Long userId, String name);

	/** 自分の中に、この行以外で同じ名前があるか。変更の重複確認に使う（自分自身の今の名前は重複としない、設計書 5.6）。 */
	boolean existsByUserIdAndNameAndIdNot(Long userId, String name, Long id);

}

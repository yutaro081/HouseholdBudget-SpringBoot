package io.github.yutaro081.householdbudget.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

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

	/** ID と user_id の両方で1件探す。他人のものや、存在しない ID は見つからない。 */
	Optional<Vendor> findByIdAndUserId(Long id, Long userId);

	/** 自分の中に、同じ名前があるか。追加の重複確認に使う（設計書 5.6）。 */
	boolean existsByUserIdAndName(Long userId, String name);

	/** 自分の中に、この行以外で同じ名前があるか。変更の重複確認に使う（自分自身の今の名前は重複としない、設計書 5.6）。 */
	boolean existsByUserIdAndNameAndIdNot(Long userId, String name, Long id);

}

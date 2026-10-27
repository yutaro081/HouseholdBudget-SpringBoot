package io.github.yutaro081.householdbudget.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.yutaro081.householdbudget.entity.Vendor;

/**
 * Vendor の読み書き（設計書 4.6）。
 * Phase 3 では、初期データの保存（save）だけを使う。
 * user_id で絞る検索などは、使う Phase（4〜6）で足す。
 */
public interface VendorRepository extends JpaRepository<Vendor, Long> {

}

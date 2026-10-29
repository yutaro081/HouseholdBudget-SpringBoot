package io.github.yutaro081.householdbudget.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.domain.Limits;
import io.github.yutaro081.householdbudget.entity.Vendor;
import io.github.yutaro081.householdbudget.exception.DeletionNotAllowedException;
import io.github.yutaro081.householdbudget.exception.DuplicateException;
import io.github.yutaro081.householdbudget.exception.LimitExceededException;
import io.github.yutaro081.householdbudget.exception.NotFoundException;
import io.github.yutaro081.householdbudget.form.VendorForm;
import io.github.yutaro081.householdbudget.repository.VendorRepository;
import io.github.yutaro081.householdbudget.repository.TransactionRepository;

/**
 * 取引先の一覧・追加・変更・削除（設計書 4.5、5.4、5.5、5.6）。科目（CategoryService）と同じ形。
 * 違う点：取引先は予算を持たないので、追加のときに予算の行は作らず、削除は「取引で使われていないこと」だけを確認する。
 * <p>
 * 【決まり】すべてのメソッドの最初の引数は userId（ログイン中のユーザーのID。Controller が LoginUser から取り出して渡す）。
 * ID で探すときは、必ず「ID と user_id」の両方で絞る。見つからなければ NotFoundException
 * （他人のものか、存在しないかは区別しない、設計書 3.9、7.8）。
 * 更新するメソッドは、1つのトランザクションで行い、途中で失敗したら全部取り消される。
 */
@Service
public class VendorService {

	private final VendorRepository vendorRepository;
	private final TransactionRepository transactionRepository;

	public VendorService(VendorRepository vendorRepository, TransactionRepository transactionRepository) {
		this.vendorRepository = vendorRepository;
		this.transactionRepository = transactionRepository;
	}

	/** 自分の取引先を、登録順で返す。 */
	@Transactional(readOnly = true)
	public List<Vendor> list(Long userId) {
		return vendorRepository.findByUserIdOrderByIdAsc(userId);
	}

	/** 自分の取引先を1件返す（変更画面用）。 */
	@Transactional(readOnly = true)
	public Vendor get(Long userId, Long id) {
		return find(userId, id);
	}

	/**
	 * 取引先を追加する。
	 *
	 * @throws LimitExceededException 自分の取引先が、すでに1人あたりの上限（256個）あるとき（256個目までは追加でき、257個目は追加できない）
	 * @throws DuplicateException 自分の中に、同じ名前がすでにあるとき
	 */
	@Transactional
	public Vendor add(Long userId, VendorForm form) {
		String name = form.getName();
		// 1人あたりの上限（256個）に達していたら、追加できない（同じ名前の確認より先に確かめる。設計書 5.4、R14）
		if (vendorRepository.countByUserId(userId) >= Limits.MAX_MASTERS_PER_USER) {
			throw new LimitExceededException("vendor limit exceeded");
		}
		if (vendorRepository.existsByUserIdAndName(userId, name)) {
			throw new DuplicateException("vendor name already exists");
		}
		try {
			return vendorRepository.saveAndFlush(new Vendor(userId, name));
		} catch (DataIntegrityViolationException e) {
			// 同時に追加されて、確認をすり抜けた場合は、DB の一意制約が拒否する（5.6）
			throw new DuplicateException("vendor name already exists", e);
		}
	}

	/**
	 * 取引先の名前を変える。自分自身の今の名前は、重複としない（名前を変えずに保存してもエラーにしない）。
	 *
	 * @throws NotFoundException  存在しない、または他人の取引先
	 * @throws DuplicateException この取引先以外で、自分の中に同じ名前があるとき
	 */
	@Transactional
	public void update(Long userId, Long id, VendorForm form) {
		Vendor vendor = find(userId, id);
		String name = form.getName();
		if (vendorRepository.existsByUserIdAndNameAndIdNot(userId, name, id)) {
			throw new DuplicateException("vendor name already exists");
		}

		vendor.rename(name);
		try {
			vendorRepository.saveAndFlush(vendor);
		} catch (DataIntegrityViolationException e) {
			throw new DuplicateException("vendor name already exists", e);
		}
	}

	/**
	 * 取引先を削除する（設計書 5.5、要件4.3）。取引で使われていれば削除できない（error.delete.inUse）。
	 *
	 * @throws NotFoundException           存在しない、または他人の取引先
	 * @throws DeletionNotAllowedException 取引で使われているとき
	 */
	@Transactional
	public void delete(Long userId, Long id) {
		Vendor vendor = find(userId, id);

		if (transactionRepository.existsByUserIdAndVendorId(userId, id)) {
			throw new DeletionNotAllowedException("error.delete.inUse");
		}

		vendorRepository.delete(vendor);
		try {
			vendorRepository.flush();
		} catch (DataIntegrityViolationException e) {
			// 判定をすり抜けて、DB の外部キーに拒否された場合（同時操作など）も、使用中として扱う（5.5）
			throw new DeletionNotAllowedException("error.delete.inUse", e);
		}
	}

	/** ID と user_id の両方で探す。なければ NotFoundException。 */
	private Vendor find(Long userId, Long id) {
		return vendorRepository.findByIdAndUserId(id, userId).orElseThrow(NotFoundException::new);
	}

}

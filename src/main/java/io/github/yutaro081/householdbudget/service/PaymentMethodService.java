package io.github.yutaro081.householdbudget.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.domain.Limits;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.exception.DeletionNotAllowedException;
import io.github.yutaro081.householdbudget.exception.DuplicateException;
import io.github.yutaro081.householdbudget.exception.LimitExceededException;
import io.github.yutaro081.householdbudget.exception.NotFoundException;
import io.github.yutaro081.householdbudget.form.PaymentMethodForm;
import io.github.yutaro081.householdbudget.repository.PaymentMethodRepository;
import io.github.yutaro081.householdbudget.repository.TransactionRepository;

/**
 * 支払方法の一覧・追加・変更・削除（設計書 4.5、5.4、5.5、5.6）。科目（CategoryService）と同じ形。
 * 違う点：支払方法は予算を持たないので、追加のときに予算の行は作らず、削除は「取引で使われていないこと」だけを確認する。
 * <p>
 * 【決まり】すべてのメソッドの最初の引数は userId（ログイン中のユーザーのID。Controller が LoginUser から取り出して渡す）。
 * ID で探すときは、必ず「ID と user_id」の両方で絞る。見つからなければ NotFoundException
 * （他人のものか、存在しないかは区別しない、設計書 3.9、7.8）。
 * 更新するメソッドは、1つのトランザクションで行い、途中で失敗したら全部取り消される。
 */
@Service
public class PaymentMethodService {

	private final PaymentMethodRepository paymentMethodRepository;
	private final TransactionRepository transactionRepository;

	public PaymentMethodService(PaymentMethodRepository paymentMethodRepository, TransactionRepository transactionRepository) {
		this.paymentMethodRepository = paymentMethodRepository;
		this.transactionRepository = transactionRepository;
	}

	/** 自分の支払方法を、登録順で返す。 */
	@Transactional(readOnly = true)
	public List<PaymentMethod> list(Long userId) {
		return paymentMethodRepository.findByUserIdOrderByIdAsc(userId);
	}

	/** 自分の支払方法を1件返す（変更画面用）。 */
	@Transactional(readOnly = true)
	public PaymentMethod get(Long userId, Long id) {
		return find(userId, id);
	}

	/**
	 * 支払方法を追加する。
	 *
	 * @throws LimitExceededException 自分の支払方法が、すでに1人あたりの上限（256個）あるとき（256個目までは追加でき、257個目は追加できない）
	 * @throws DuplicateException 自分の中に、同じ名前がすでにあるとき
	 */
	@Transactional
	public PaymentMethod add(Long userId, PaymentMethodForm form) {
		String name = form.getName();
		// 1人あたりの上限（256個）に達していたら、追加できない（同じ名前の確認より先に確かめる。設計書 5.4、R14）
		if (paymentMethodRepository.countByUserId(userId) >= Limits.MAX_MASTERS_PER_USER) {
			throw new LimitExceededException("paymentMethod limit exceeded");
		}
		if (paymentMethodRepository.existsByUserIdAndName(userId, name)) {
			throw new DuplicateException("paymentMethod name already exists");
		}
		try {
			return paymentMethodRepository.saveAndFlush(new PaymentMethod(userId, name));
		} catch (DataIntegrityViolationException e) {
			// 同時に追加されて、確認をすり抜けた場合は、DB の一意制約が拒否する（5.6）
			throw new DuplicateException("paymentMethod name already exists", e);
		}
	}

	/**
	 * 支払方法の名前を変える。自分自身の今の名前は、重複としない（名前を変えずに保存してもエラーにしない）。
	 *
	 * @throws NotFoundException  存在しない、または他人の支払方法
	 * @throws DuplicateException この支払方法以外で、自分の中に同じ名前があるとき
	 */
	@Transactional
	public void update(Long userId, Long id, PaymentMethodForm form) {
		PaymentMethod paymentMethod = find(userId, id);
		String name = form.getName();
		if (paymentMethodRepository.existsByUserIdAndNameAndIdNot(userId, name, id)) {
			throw new DuplicateException("paymentMethod name already exists");
		}

		paymentMethod.rename(name);
		try {
			paymentMethodRepository.saveAndFlush(paymentMethod);
		} catch (DataIntegrityViolationException e) {
			throw new DuplicateException("paymentMethod name already exists", e);
		}
	}

	/**
	 * 支払方法を削除する（設計書 5.5、要件4.3）。取引で使われていれば削除できない（error.delete.inUse）。
	 *
	 * @throws NotFoundException           存在しない、または他人の支払方法
	 * @throws DeletionNotAllowedException 取引で使われているとき
	 */
	@Transactional
	public void delete(Long userId, Long id) {
		PaymentMethod paymentMethod = find(userId, id);

		if (transactionRepository.existsByUserIdAndPaymentMethodId(userId, id)) {
			throw new DeletionNotAllowedException("error.delete.inUse");
		}

		paymentMethodRepository.delete(paymentMethod);
		try {
			paymentMethodRepository.flush();
		} catch (DataIntegrityViolationException e) {
			// 判定をすり抜けて、DB の外部キーに拒否された場合（同時操作など）も、使用中として扱う（5.5）
			throw new DeletionNotAllowedException("error.delete.inUse", e);
		}
	}

	/** ID と user_id の両方で探す。なければ NotFoundException。 */
	private PaymentMethod find(Long userId, Long id) {
		return paymentMethodRepository.findByIdAndUserId(id, userId).orElseThrow(NotFoundException::new);
	}

}

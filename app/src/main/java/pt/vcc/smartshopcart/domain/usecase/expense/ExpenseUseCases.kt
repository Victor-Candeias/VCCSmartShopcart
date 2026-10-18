package pt.vcc.smartshopcart.domain.usecase.expense

import pt.vcc.smartshopcart.core.common.IdProvider
import pt.vcc.smartshopcart.core.common.TimeProvider
import pt.vcc.smartshopcart.domain.model.expense.ExpenseDetails
import pt.vcc.smartshopcart.domain.model.expense.ExpenseDraft
import pt.vcc.smartshopcart.domain.model.expense.ExpenseDraftItem
import pt.vcc.smartshopcart.domain.model.expense.ExpenseEditorDraft
import pt.vcc.smartshopcart.domain.model.expense.ExpenseEditorLine
import pt.vcc.smartshopcart.domain.model.expense.ExpenseSummary
import pt.vcc.smartshopcart.domain.repository.ExpenseDraftStore
import pt.vcc.smartshopcart.domain.repository.ExpenseRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject

sealed interface ExpenseValidation {
    data class Valid(val draft: ExpenseDraft) : ExpenseValidation
    data object NoItems : ExpenseValidation
    data object MissingPrice : ExpenseValidation
    data object InvalidValues : ExpenseValidation

    companion object {
        /** Pelo menos um item, quantidades ≥ 1 e preços definidos e ≥ 0. */
        fun validate(draft: ExpenseEditorDraft): ExpenseValidation {
            if (draft.lines.isEmpty()) return NoItems
            if (draft.lines.any { it.unitPriceCents == null }) return MissingPrice
            if (draft.lines.any { it.name.isBlank() || it.quantity < 1 || it.unitPriceCents!! < 0 }) return InvalidValues
            val items = draft.lines.map { ExpenseDraftItem(it.productId, it.name.trim(), it.quantity, it.unitPriceCents!!) }
            val total = runCatching { items.fold(0L) { acc, item -> Math.addExact(acc, item.totalCents) } }
            if (total.isFailure) return InvalidValues
            return Valid(
                ExpenseDraft(
                    establishmentName = draft.establishmentName.trim().ifEmpty { null },
                    occurredAt = draft.occurredAt,
                    items = items,
                    notes = draft.notes.trim().ifEmpty { null },
                ),
            )
        }
    }
}

sealed interface SaveExpenseResult {
    data class Saved(val expenseId: String, val totalCents: Long) : SaveExpenseResult
    data class Invalid(val reason: ExpenseValidation) : SaveExpenseResult
}

/**
 * Valida e grava (cria ou, com `editingExpenseId`, atualiza) a despesa numa transação e limpa o
 * rascunho local. Valores inválidos nunca chegam à base de dados.
 */
class SaveExpenseUseCase @Inject constructor(
    private val repository: ExpenseRepository,
    private val drafts: ExpenseDraftStore,
) {
    suspend operator fun invoke(draft: ExpenseEditorDraft): SaveExpenseResult {
        val validation = ExpenseValidation.validate(draft)
        if (validation !is ExpenseValidation.Valid) return SaveExpenseResult.Invalid(validation)
        val id = repository.saveExpense(validation.draft, draft.editingExpenseId)
        drafts.clear()
        return SaveExpenseResult.Saved(id, validation.draft.totalCents)
    }
}

/** Nova despesa à hora atual, opcionalmente com uma categoria ou um produto já escolhidos. */
class StartExpenseDraftUseCase @Inject constructor(
    private val repository: ExpenseRepository,
    private val suggestPrice: SuggestProductPriceUseCase,
    private val time: TimeProvider,
    private val ids: IdProvider,
) {
    suspend operator fun invoke(categoryId: String? = null, productId: String? = null): ExpenseEditorDraft {
        val product = productId?.let { repository.getProduct(it) }
        val draft = ExpenseEditorDraft(occurredAt = time.now(), selectedCategoryId = product?.categoryId ?: categoryId)
        if (product == null) return draft
        val suggestion = suggestPrice(product.id, null)
        return draft.addProduct(product, ids.newId(), suggestion.cents, suggestion.source)
    }
}

/** Rascunho de edição de uma despesa guardada: mantém data/hora, estabelecimento, itens e notas. */
class LoadExpenseDraftUseCase @Inject constructor(
    private val repository: ExpenseRepository,
    private val ids: IdProvider,
) {
    suspend operator fun invoke(expenseId: String): ExpenseEditorDraft? =
        repository.getExpense(expenseId)?.toEditorDraft(repository, ids)?.copy(editingExpenseId = expenseId)
}

/**
 * "Repetir despesa": novo rascunho com a data/hora atuais e os mesmos produtos e quantidades;
 * os preços ficam editáveis antes de guardar.
 */
class RepeatExpenseUseCase @Inject constructor(
    private val repository: ExpenseRepository,
    private val suggestPrice: SuggestProductPriceUseCase,
    private val time: TimeProvider,
    private val ids: IdProvider,
) {
    /** Os preços são sugestões (§7) com recurso ao preço pago na despesa original. */
    suspend operator fun invoke(expenseId: String): ExpenseEditorDraft? {
        val original = repository.getExpense(expenseId)?.toEditorDraft(repository, ids) ?: return null
        val lines = original.lines.map { line ->
            val suggestion = suggestPrice(line.productId, original.establishmentName, line.unitPriceCents)
            line.copy(unitPriceCents = suggestion.cents, priceSource = suggestion.source.takeIf { suggestion.cents != null })
        }
        return original.copy(occurredAt = time.now(), lines = lines)
    }
}

class DeleteExpenseUseCase @Inject constructor(private val repository: ExpenseRepository) {
    suspend operator fun invoke(expenseId: String) = repository.deleteExpense(expenseId)
}

private suspend fun ExpenseDetails.toEditorDraft(repository: ExpenseRepository, ids: IdProvider): ExpenseEditorDraft {
    val firstCategory = items.firstNotNullOfOrNull { item -> item.productId?.let { repository.getProduct(it)?.categoryId } }
    return ExpenseEditorDraft(
        establishmentName = establishment?.name.orEmpty(),
        occurredAt = expense.occurredAt,
        lines = items.map { ExpenseEditorLine(ids.newId(), it.productId, it.nameSnapshot, it.quantity, it.unitPriceCents) },
        notes = expense.notes.orEmpty(),
        selectedCategoryId = firstCategory,
    )
}

/** Filtros do histórico de despesas (§4.4). */
object ExpenseHistoryFilter {
    fun apply(expenses: List<ExpenseSummary>, categoryId: String?, establishment: String?): List<ExpenseSummary> =
        expenses.filter { summary ->
            (categoryId == null || categoryId in summary.categoryIds) &&
                (establishment == null || summary.establishmentName.equals(establishment, ignoreCase = true))
        }

    /** Estabelecimentos distintos presentes no histórico, por ordem alfabética. */
    fun establishments(expenses: List<ExpenseSummary>): List<String> =
        expenses.mapNotNull { it.establishmentName }.distinctBy { it.lowercase() }.sortedBy { ExpenseSearch.normalize(it) }
}

/** Ajusta data e hora de `occurredAt` (epoch millis UTC) no fuso local. */
object ExpenseDateTime {
    /** [utcDateMillis] é a meia-noite UTC devolvida pelo DatePicker. */
    fun withDate(occurredAt: Long, utcDateMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val date = Instant.ofEpochMilli(utcDateMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val current = Instant.ofEpochMilli(occurredAt).atZone(zone)
        return current.with(date).toInstant().toEpochMilli()
    }

    fun withTime(occurredAt: Long, hour: Int, minute: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val current = Instant.ofEpochMilli(occurredAt).atZone(zone)
        return current.with(LocalTime.of(hour, minute)).toInstant().toEpochMilli()
    }

    /** Meia-noite UTC da data local de [occurredAt], para inicializar o DatePicker. */
    fun utcDateMillis(occurredAt: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val date: LocalDate = Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalDate()
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    fun localTime(occurredAt: Long, zone: ZoneId = ZoneId.systemDefault()): LocalTime =
        Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalTime()
}

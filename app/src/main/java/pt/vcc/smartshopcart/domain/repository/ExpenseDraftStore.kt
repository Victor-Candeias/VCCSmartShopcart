package pt.vcc.smartshopcart.domain.repository

import kotlinx.coroutines.flow.Flow
import pt.vcc.smartshopcart.domain.model.expense.ExpenseEditorDraft

/** Rascunho local do editor de despesas (um de cada vez), mantido até guardar ou cancelar. */
interface ExpenseDraftStore {
    val draft: Flow<ExpenseEditorDraft?>
    suspend fun get(): ExpenseEditorDraft?
    suspend fun save(draft: ExpenseEditorDraft)
    suspend fun clear()
}

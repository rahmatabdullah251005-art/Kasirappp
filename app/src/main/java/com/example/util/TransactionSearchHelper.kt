package com.example.util

import com.example.data.remote.CloudSaleWithItems

object TransactionSearchHelper {

    fun filterTransactions(
        transactions: List<CloudSaleWithItems>,
        rawQuery: String
    ): List<CloudSaleWithItems> {
        val tokens = rawQuery.trim()
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        if (tokens.isEmpty()) return transactions

        return transactions.filter { txWithItems ->
            val searchable = buildSearchableCorpus(txWithItems)
            tokens.all { token -> searchable.contains(token) }
        }
    }

    private fun buildSearchableCorpus(txWithItems: CloudSaleWithItems): String {
        val tx = txWithItems.transaction
        val parts = buildList {
            add(tx.invoiceNumber)
            add(tx.dateKey)
            add(DateUtils.formatDateTime(tx.timestampMillis))
            add(DateUtils.formatDisplayDate(tx.dateKey))
            add(DateUtils.formatShortDate(tx.dateKey))
            add(DateUtils.formatShortDay(tx.dateKey))
            add(tx.customerName)
            add(tx.cashierName)
            add(tx.notes)
            add(tx.paymentMethodEnum.displayName)
            add(tx.qrisReference)
            txWithItems.items.forEach { item ->
                add(item.productName)
                add(item.variantSummary)
                add(item.skuCode)
                add(item.categoryName)
            }
        }
        return parts.joinToString(" ").lowercase()
    }
}

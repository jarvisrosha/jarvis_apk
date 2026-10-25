package com.example.jarvis

import android.content.Context
import android.provider.ContactsContract

object ContactHelper {
    // फोनमधील सेव्ह केलेल्या नावावरून नंबर शोधणे
    fun findPhoneNumberByName(context: Context, nameQuery: String): Pair<String, String>? {
        val resolver = context.contentResolver
        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$nameQuery%"),
            null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val matchedName = it.getString(0) ?: nameQuery
                val matchedNumber = it.getString(1)?.replace("\s".toRegex(), "") ?: ""
                if (matchedNumber.isNotEmpty()) {
                    return Pair(matchedName, matchedNumber)
                }
            }
        }
        return null
    }
}

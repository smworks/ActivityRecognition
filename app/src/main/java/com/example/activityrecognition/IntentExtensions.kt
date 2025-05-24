@file:Suppress("DEPRECATION")

package com.example.activityrecognition

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable


fun Intent.getInfo(): String {
    return toString() + " " + bundleToString(extras)
}

fun bundleToString(bundle: Bundle?): String {
    val out = StringBuilder("Bundle[")

    if (bundle == null) {
        out.append("null")
    } else {
        var first = true
        for (key in bundle.keySet()) {
            if (!first) {
                out.append(", ")
            }

            out.append(key).append('=')

            val value = bundle.get(key)

            if (value is IntArray) {
                out.append(value.contentToString())
            } else if (value is ByteArray) {
                out.append(value.contentToString())
            } else if (value is BooleanArray) {
                out.append(value.contentToString())
            } else if (value is ShortArray) {
                out.append(value.contentToString())
            } else if (value is LongArray) {
                out.append(value.contentToString())
            } else if (value is FloatArray) {
                out.append(value.contentToString())
            } else if (value is DoubleArray) {
                out.append(value.contentToString())
            } else if (value is Array<*> && value.isArrayOf<String>()) {
                out.append(value.contentToString())
            } else if (value is Array<*> && value.isArrayOf<CharSequence>()) {
                out.append(value.contentToString())
            } else if (value is Array<*> && value.isArrayOf<Parcelable>()) {
                out.append(value.contentToString())
            } else if (value is Bundle) {
                out.append(bundleToString(value))
            } else {
                out.append(value)
            }

            first = false
        }
    }

    out.append("]")
    return out.toString()
}
package org.sharenow.fileshare.platform

import java.util.Collections
import java.util.Enumeration

internal fun <T> Enumeration<T>.toList(): List<T> = Collections.list(this)

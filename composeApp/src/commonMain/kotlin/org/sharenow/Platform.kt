package org.sharenow

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
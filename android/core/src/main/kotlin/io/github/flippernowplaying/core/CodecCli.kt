package io.github.flippernowplaying.core
fun main() { generateSequence(::readLine).forEach { line ->
 val result=runCatching { require(line.length%2==0&&line.length<=1576);Frame.decode(line.chunked(2).map { it.toInt(16).toByte() }.toByteArray()).encode().joinToString("") { "%02x".format(it.toInt() and 255) } }.getOrDefault("INVALID")
 println(result)
} }

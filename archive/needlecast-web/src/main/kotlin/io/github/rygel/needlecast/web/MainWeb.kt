package io.github.rygel.needlecast.web

object MainWeb {
    @JvmStatic
    fun main(args: Array<String>) {
        val port = args.firstOrNull()?.toIntOrNull() ?: 4000
        WebUiServer(port).start()
    }
}

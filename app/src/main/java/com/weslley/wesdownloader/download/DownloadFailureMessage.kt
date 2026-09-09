package com.weslley.wesdownloader.download

object DownloadFailureMessage {
    fun from(error: Throwable): String {
        val detail = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()

        return when {
            "no space" in detail || "errno 28" in detail ->
                "O aparelho ficou sem espaco durante o download."
            "ffmpeg" in detail || "ffprobe" in detail || "postprocess" in detail || "merger" in detail ->
                "Falha ao combinar as faixas de video e audio. Tente novamente."
            "403" in detail || "forbidden" in detail ->
                "O YouTube interrompeu a transferencia. Tente novamente para retomar."
            "requested format" in detail || "format is not available" in detail ->
                "A qualidade escolhida deixou de estar disponivel. Analise o link novamente."
            "permission denied" in detail || "unable to create" in detail || "unable to open" in detail ->
                "Nao foi possivel salvar o arquivo no aparelho."
            "primary directory" in detail || "not allowed for content" in detail ->
                "O Android nao permitiu salvar o arquivo na pasta escolhida."
            else -> "Nao foi possivel concluir o download. Tente novamente."
        }
    }
}

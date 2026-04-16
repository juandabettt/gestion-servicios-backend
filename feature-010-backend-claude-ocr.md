La llamada a Claude API responde 400 Bad Request. Busca ClaudeOcrServiceAdapter.java y corrige lo siguiente:

Cambia el modelo de claude-opus-4-5 a claude-opus-4-5-20251101 — ese es el nombre exacto del modelo
Verifica que el formato del source de la imagen sea exactamente así:

json{
  "type": "image",
  "source": {
    "type": "url",
    "url": "https://..."
  }
}

Agrega un log que imprima el body completo de la request antes de enviarla a Claude para poder debuggear
No toques ningún otro archivo.

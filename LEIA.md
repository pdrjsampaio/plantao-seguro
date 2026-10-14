# APKs assinados para publicação

Este branch guarda APKs já assinados com a chave do projeto, usados pelo
workflow "Publicar versão" quando o secret `PS_CHAVE` não está configurado.
O workflow confere o certificado (SHA-256
`F7:E8:77:22:7A:C1:5A:26:B8:8E:B9:75:F7:37:BD:BD:AB:4B:E0:66:2D:2E:FE:15:19:18:72:DC:56:63:53:73`)
e a versão antes de publicar.

Para instalar o app, use a página de versões (Releases), não este branch.

| Pasta | Origem |
|---|---|
| `v3.0.0-beta.1/` | `app-release-unsigned.apk` da compilação do commit 623dfda (GitHub Actions, execução 38548373158), assinado com `apksigner` (esquemas v2 e v3). |
